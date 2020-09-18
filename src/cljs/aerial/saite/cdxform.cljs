(ns aerial.saite.cdxform
  (:require
   [cljs.core.async
    :as async
    :refer (<! >! put! chan)
    :refer-macros [go go-loop]]

   [clojure.string :as cljstr]

   [cljs.tools.reader :refer [read-string]]

   [com.rpl.specter :as sp]

   [aerial.hanami.core :as hmi
    :refer [printchan]]
   [aerial.hanami.common :as hc
    :refer [RMV]]

   [aerial.saite.savrest
    :refer [update-ddb get-ddb]]
   [aerial.saite.compiler
    :refer [format evaluate]])
  )


(def LIST-NODES
  (sp/recursive-path
   [] p
   (sp/if-path
    #(or (list? %) (= (type %) cljs.core/LazySeq))
    (sp/continue-then-stay sp/ALL p))))


#_(let [cm @dbg-cm] (read-string (str \( (.getValue cm) "\n" \))))
(defn get-all-cm-as-code [cm]
  (try
    (read-string (str \( (.getValue cm) "\n" \)))
    (catch js/Error e
      (js/alert (str "Check for miss-matched parens / brackets\n\n" e))
      '(:bad-code))))


(defn eval-on-jvm [src cb]
  (let [ch (async/chan)
        chankey (keyword (gensym "chan-"))
        res (volatile! nil)
        tid (hmi/get-cur-tab :id)
        nssym (get (get-ddb [:tabs :extns tid]) :ns 'aerial.saite.compiler)
        eid (get-ddb [:tabs :extns tid :eid])
        throbber (or (get-ddb [:editors tid eid :opts :throbber]) (atom false))]
    (update-ddb [:main :chans chankey] ch)
    (hmi/send-msg {:op :eval-clj
                   :data {:uid (hmi/get-adb [:main :uid])
                          :eval true
                          :chankey chankey
                          :nssym nssym
                          :code src}})
    (reset! throbber true)
    (go (vreset! res (async/<! ch))
        (reset! throbber false)
        (update-ddb [:main :chans chankey] :rm)
        (cb @res))))



(defn shost-sym-info [nssym sym]
  (if (-> sym name first (= ".")) ; ClJS bug
    {:fn? false :minfo nil}
    (let [symval (volatile! nil)
          _ (evaluate nssym (format "(resolve '%s)" sym)
                      (fn[m] (vswap! symval (fn[_] (m :value)))))]
      (if (not @symval)
        {:fn? false :minfo nil}
        (let [_ (evaluate nssym sym (fn[m] (vswap! symval (fn[_] (m :value)))))
              v @symval]
          {:fn? (fn? v) :minfo (meta v)})))))

(defn cljfm? [nssym l]
  (when (symbol? (first l))
    (let [sym (read-string (str (name nssym) "/" (name (first l))))
          info (shost-sym-info nssym sym)]
      (and (info :fn?) (get-in info [:minfo :clj])))))

(defn run-prom-chain [prom bodyfn]
  (if (instance? js/Promise prom)
    (.then prom (fn[res] (run-prom-chain res bodyfn)))
    (bodyfn prom)))

(defn promfm [fm]
  `(.then ~fm (~'fn[~'res] (sc/run-prom-chain ~'res (~'fn[~'res] :tail)))))

(defn group-fms [nssym fms chunks]
  (if (not (seq fms))
    chunks
    (if (cljfm? nssym (first fms))
      (group-fms nssym (rest fms)
                 (conj chunks (promfm (first fms))))
      (let [g (take-while #(not (cljfm? nssym %)) fms)
            tail (drop-while #(not (cljfm? nssym %)) fms)]
        (group-fms nssym tail
                   (conj chunks
                         (cons 'do (sp/setval sp/AFTER-ELEM :tail g))))))))

(defn reduce-in-bodies [fms]
  (let [fms (reverse fms)]
    (reduce (fn[FM fm]
              (hc/xform
               fm
               :aerial.hanami.common/spec {}
               :aerial.hanami.common/use-defaults? false
               :tail FM))
            (if (-> fms ffirst (= 'do)) sp/NONE 'res)
            fms)))

(defn xform-clj [clj? code clj-data]
  #_(js/console.log "CODE" code #_(type code))
  (sp/transform
   sp/ALL
   (fn[v] #_(printchan v (@clj-data :in-clj))
     (cond
       (and (or (list? v) (= (type v) LazySeq))
            (list? (first v)) (-> v ffirst (= 'clj)))
       (let [clj-fm (xform-clj clj?  [(first v)] clj-data)
             tail (cons 'do (xform-clj clj?  (rest v) clj-data))]
         (hc/xform (first clj-fm) :tail tail))


       (and (list? v) (= (first v) 'clj))
       (let [_ (swap! clj-data
                      (fn[m] (assoc m :in-clj true :jvm-syms #{} :%s [])))
             body (if (list? (last v))
                    (xform-clj clj?  (last v) clj-data)
                    (last v))
             jvm-syms (@clj-data :%s)
             body (if (seq jvm-syms)
                    `(~'apply ~'format ~(pr-str body) ~(@clj-data :%s))
                    (pr-str body))
             _ (swap! clj-data (fn[m] (dissoc m :in-clj :jvm-syms)))
             body (hc/xform '(-> (sc/selfhost-jvm-eval :body)
                                 (.then (fn[res]
                                          (vswap! $state$
                                                  (fn[s] (assoc s 'res res)))
                                          :tail)))
                            :aerial.hanami.common/use-defaults? false
                            :body body)
             fmkey (keyword (gensym "cljcode-"))]
         (vreset! clj? true)
         (when (@clj-data :defn)
           (swap! clj-data (fn[m] (assoc-in m [:defn :clj] true))))
         (swap! clj-data (fn[m] (dissoc m :in-clj)))
         #_(println :CLJBODY body)
         body)

       (and (list? v) (cljfm? (@clj-data :nssym) v))
       (do (printchan v @clj-data)
           (when (@clj-data :defn)
             (swap! clj-data (fn[m] (assoc-in m [:defn :clj] true))))
           (promfm (cons (first v) (xform-clj clj? (rest v) clj-data))))


       (and (list? v) (= (first v) 'let) (get @clj-data :in-clj))
       (let [bindvec (second v)
             jvm-syms (@clj-data :jvm-syms)]
         (swap! clj-data
                (fn[m] (assoc m :jvm-syms
                              (reduce (fn[jsyms [k fm]]
                                        (conj jsyms k))
                                      jvm-syms (partition-all 2 bindvec)))))
         `(~'let ~bindvec
           ~@(xform-clj clj?  (drop 2 v) clj-data)))


       (and (list? v) (= (first v) 'let) (not (@clj-data :in-clj)))
       (let [_ (reset! clj-data
                       (assoc @clj-data
                              :syms (reduce (fn[syms [k _]]
                                              (assoc syms k true))
                                            (get @clj-data :syms {})
                                            (->> v second (partition-all 2)))))
             bindvec (second v)
             clj-keys (->> bindvec
                           (partition-all 2)
                           (keep (fn[[k fm]]
                                   (when (and (list? fm)
                                              (or (cljfm? (@clj-data :nssym) fm)
                                                  (-> fm first (= 'clj))))
                                     k)))
                           (into #{}))
             ;;_ (printchan :CLJ-KEYS clj-keys)
             bindings (xform-clj clj?  bindvec clj-data)
             bindpairs (->> bindings
                            (partition-all 2))
             cljpairs (->> bindpairs (filter (fn[[k _]] (clj-keys k)))
                           (map (fn[[k v]] `('~k ~v))))
             bindpairs (->> bindpairs
                            (filter (fn[[k _]] (-> k clj-keys not))))
             ;;_ (println :BINDPAIRS (apply concat bindpairs))
             ;;_ (printchan :CLJPAIRS cljpairs)
             addstate `(~'vswap! ~'$state$
                        (~'fn[~'s]
                         (~'apply ~'assoc ~'s
                          ~(cons 'list
                                 (->> bindpairs
                                      (map (fn[[k v]] `('~k ~k)))
                                      (apply concat))))))
             body (xform-clj clj?  (drop 2 v) clj-data)]
         ;;(printchan :BODY body)
         (swap! clj-data
                (fn[m] (assoc m :bindings (cons bindings (m :bindings ())))))
         (if (seq clj-keys)
           `(do
              (~'let ~(vec (apply concat bindpairs))
               ~addstate
               ~(reduce (fn[FM fm]
                          (hc/xform
                           fm
                           :aerial.hanami.common/spec {}
                           :aerial.hanami.common/use-defaults? false
                           :tail FM))
                        (cons 'do body)
                        (->> cljpairs
                             (map (fn[[k fm]]
                                    (hc/xform
                                     fm
                                     :aerial.hanami.common/spec {}
                                     :aerial.hanami.common/use-defaults? false
                                     'res (second k))))
                             reverse))))
           `(~'let ~bindings ~addstate ~@body)))


       (and (list? v) (= (first v) 'defn))
       (let [name (second v)
             _ (reset! clj-data
                       (assoc @clj-data
                              :syms (reduce (fn[syms k]
                                              (assoc syms k true))
                                            (get @clj-data :syms {})
                                            (-> v rest second))
                              :defn {}))
             bindings (xform-clj clj?  (-> v rest second) clj-data)
             bindpairs (cons 'list
                             (apply concat
                                    (map (fn[s] `('~s ~s)) bindings)))
             ;;_ (println :BINDPAIRS bindpairs)
             addstate `(~'vswap! ~'$state$
                                (~'fn[~'s] (~'apply ~'assoc ~'s ~bindpairs)))
             body (xform-clj clj?  (drop 3 v) clj-data)
             cljfn? (get-in @clj-data [:defn :clj])]
         (swap! clj-data (fn[m] (dissoc m :defn)))
         (if cljfn?
           `(do (~'defn ~name ~bindings ~addstate ~@body)
                (~'def ~name (~'with-meta ~name {:clj true})))
           `(~'defn ~name ~bindings ~addstate ~@body)))


       (and (list? v) (= (first v) 'def) (not (@clj-data :in-clj)))
       (let [name (second v)
             _ (swap! clj-data (fn[m] (assoc m :defn {})))
             body (xform-clj clj?  (drop 2 v) clj-data)
             ;;_ (js/console.log "BODY" body)
             cljfn? (get-in @clj-data [:defn :clj])
             _ (swap! clj-data (fn[m] (dissoc m :defn)))
             body (if (and (coll? body) (coll? (first body))
                           (= 1 (count body)) (or @clj? cljfn?))
                    (first body)
                    body)]
         (cond
           (and (or @clj? cljfn?)
                (seq (sp/select [LIST-NODES  #(= (last %) :tail)] body)))
           (hc/xform body
                     {:aerial.hanami.common/use-defaults? false
                      :aerial.hanami.common/spec {}
                      :tail `(do :a (~'def ~name ~'res) '~name)})

           (or cljfn? @clj?)
           `(do :b (~'def ~name ~body)
                (sc/run-prom-chain
                 ~name
                 (~'fn[~'res]
                  (~'def ~name ~'res)))
                '~name)
           :else
           `(~'def ~name ~@body)))

       #_(and (list? v) (#{'and 'or} (first v)) (not (@clj-data :in-clj)))
       #_(if (some #(and (list? %) (= (first %) 'clj)) (rest v))
         (let [op (first v)
               sym-exprs (mapv #(vector (gensym "boo-") %) (rest v))]
           ))


       (coll? v)
       (let [fm1 (first v)
             fm2 (second v)
             fm3 (-> v rest second)
             nssym (@clj-data :nssym)]
         (cond
           ;; *** NYI *** DOES NOT WORK. SEEMS CRAZY ANYWAY
           ;; vectors of intermixed clj and client forms
           (and (list? fm1) (-> fm1 first (= 'clj)))
           (let [cljfm (first (xform-clj clj?  (take 1 v) clj-data))
                 tailfms (xform-clj clj?  (rest v) clj-data)
                 fms (mapv #(vector (gensym "c-") %) (cons cljfm tailfms))]
             (reduce
              (fn[FM [k fm]] (printchan FM fm)
                (hc/xform
                 (hc/xform fm 'res k)
                 {:aerial.hanami.common/use-defaults? false
                  :aerial.hanami.common/spec {}
                  :tail FM :next k}))
              (let [[k fm] (first fms)]
                [k (hc/xform
                    fm {:aerial.hanami.common/use-defaults? false
                        :aerial.hanami.common/spec {}
                        'res k
                        :tail [k :next]})])
              (rest fms)))

           ;; Things like `(rest (clj ...))`
           (and (list? fm2)
                (or (-> fm2 first (= 'clj)) (cljfm? nssym fm2)))
           (let [cljfm (first (if (cljfm? nssym fm2)
                                (xform-clj clj? (list fm2) clj-data)
                                (xform-clj clj?  (rest v) clj-data)))]
             (hc/xform
              cljfm
              {:aerial.hanami.common/use-defaults? false
               :aerial.hanami.common/spec {}
               :tail `(~fm1 ~'res)}))

           ;; Things like `(take 10 (clj ...))`
           (and (list? fm3)
                (or (-> fm3 first (= 'clj))
                    (cljfm? nssym fm3)))
           (let [cljfm (first (if (cljfm? nssym fm3)
                                (xform-clj clj? (list fm3) clj-data)
                                (xform-clj clj?  (drop 2 v) clj-data)))]
             (hc/xform
              cljfm
              {:aerial.hanami.common/use-defaults? false
               :aerial.hanami.common/spec {}
               :tail `(~fm1 ~fm2 ~'res)}))
           :else
           (xform-clj clj?  v clj-data)))


       (and (symbol? v) (get @clj-data :in-clj))
       (cond
         (get-in @clj-data [:jvm-syms v]) v

         (get-in @clj-data [:syms v])
         (do
           (swap! clj-data
                  (fn[m] (assoc m :%s (conj (m :%s) `((deref ~'$state$) '~v)))))
           '%s)

         :else v)

       (and (symbol? v) (@clj-data :defn))
       (let [{:keys [fn? minfo]} (shost-sym-info (@clj-data :nssym) v)]
         #_(printchan :XX @clj-data v fn? minfo)
         (when (and fn? minfo (minfo :clj))
           (swap! clj-data (fn[m] (assoc-in m [:defn :clj] true))))
         v)

       :else v))
   code))




(defn get-xform-code-stg [nssym cd]
  (let [clj? (volatile! false)
        clj-data (atom {:nssym nssym})
        cd (list 'do cd)
        code (xform-clj clj? cd clj-data)
        submap {:aerial.hanami.common/use-defaults? false
                :aerial.hanami.common/spec {}
                :body code
                :tail 'res}]
    (str (hc/xform '(let [$state$ (volatile! {})] :body)
                   submap))))

(defn eval-mixed [dnch nssym cb fms]
  (go
    (if (not (seq fms))
      (async/>! dnch :done)
      (let [[fm & tail] fms
            resch (chan)
            chcb (fn[v] (go (async/>! resch v)))]
        (do (evaluate nssym (get-xform-code-stg nssym fm) chcb)
            (let [res (async/<! resch)]
              (if (instance? js/Promise (res :value))
                (.then (res :value)
                       (fn[val]
                         (cb {:value val})
                         (eval-mixed dnch nssym cb tail)))
                (do (cb res)
                    (eval-mixed dnch nssym cb tail)))))))))

(defn eval-chunks [dnchan nssym cb spinr chunks]
  (go
    (if (seq chunks)
      (let [[kw fms] (first chunks)
            tail (rest chunks)
            resch (chan)
            chcb (fn[v] (go (async/>! resch v)))]
        (case kw
          :cljs (do (evaluate nssym fms chcb)
                    (cb (async/<! resch))
                    (eval-chunks dnchan nssym cb spinr tail))
          :clj (do (eval-on-jvm fms chcb)
                   (cb (async/<! resch))
                   (eval-chunks dnchan nssym cb spinr tail))
          :mixed (let [dnch (chan)]
                   (eval-mixed dnch nssym cb fms)
                   (hmi/printchan :MRES  (async/<! dnch))
                   (eval-chunks dnchan nssym cb spinr tail))
          (do (cb {:value (format "%s : unknown code keyword" kw)})
              (eval-chunks dnchan nssym cb spinr tail))))
      (do (reset! spinr false)
          (async/>! dnchan :done)))))


(defn get-code-chunks [forms chunks]
  (let [codekws #{:clj :cljs :mixed :none}]
    (if (seq forms)
      (let [nxt (->> forms
                     (drop-while #(-> % codekws not)))
            kw (first nxt)
            ;;_ (println kw nxt)
            [fms tail] (loop [tail (rest nxt) fms []]
                         (if (or (-> tail seq not)
                                 (codekws (first tail)))
                           [fms tail]
                           (recur (rest tail)
                                  (conj fms (first tail)))))]
        (get-code-chunks tail (conj chunks [kw fms])))
      chunks)))

(defn get-chunks [all-forms]
  (let [chunks (filter (fn[[kw fms]] (not= kw :none))
                       (get-code-chunks all-forms []))]
    (mapv (fn[[kw fms]]
            (if (= kw :mixed)
              [kw fms] ; mixed must be done individually as eval'd
              [kw (str "(do " (cljstr/join "\n" fms) ")")]))
          chunks)))


(defn eval-all []
  (let [tid (hmi/get-cur-tab :id)
        nssym (get (get-ddb [:tabs :extns tid]) :ns 'aerial.saite.compiler)
        eid (get-ddb [:tabs :extns tid :eid])
        cm (get-ddb [:tabs :cms tid eid :$ed])
        cb cm.CB
        spinr (or (get-ddb [:editors tid eid :opts :throbber]) (atom false))
        all-forms (get-all-cm-as-code cm)
        chunks (get-chunks all-forms)
        dnchan (chan)]
    (if (not (ffirst chunks))
      (cb {:value "No code type indicators found - stopping load"})
      (do (reset! spinr true)
          (eval-chunks dnchan nssym cb spinr chunks)))))


(defn on-load-eval []
  (let [tabs-chunks (-> (get-ddb [:tabs :extns]) (dissoc :$split)
                        (->> (mapv (fn[[tid v]]
                                     [tid (v :ns)
                                      (rest (read-string
                                             (str "(list " (v :src) ")")))]))
                             (mapv (fn[[tid ns forms]]
                                     [tid ns (get-chunks forms)]))))
        spinr (or (get-ddb [:main :throbber]) (atom false))
        cb #(printchan %)
        dnchan (chan)]
    (go
      (doseq [[tid nssym chunks] tabs-chunks]
        (if (not (ffirst chunks))
          (cb {:value
               (format "%s: No code type indicators found - stopping load"
                       tid)})
          (do
            (reset! spinr true)
            (eval-chunks dnchan nssym cb spinr chunks)
            (async/<! dnchan)))))))
