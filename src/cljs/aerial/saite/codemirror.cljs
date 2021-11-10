(ns  aerial.saite.codemirror
  (:require
   [cljs.core.async
    :as async
    :refer (<! >! put! chan)
    :refer-macros [go go-loop]]
   [clojure.string :as cljstr]

   [cljs.tools.reader :refer [read-string]]

   [aerial.hanami.core :as hmi
    :refer [printchan md]]
   [aerial.hanami.common :as hc
    :refer [RMV]]
   [aerial.hanami.templates :as ht]

   [aerial.saite.savrest
    :refer [update-ddb get-ddb]]
   [aerial.saite.compiler
    :refer [format evaluate load-analysis-cache!]]
   [aerial.saite.cdxform
    :refer [get-all-cm-as-code xform-clj
            eval-on-jvm eval-all on-load-eval]]
   [aerial.saite.tabops :as tops
    :refer [push-undo undo redo]]
   [aerial.saite.splits :as ass]

   [com.rpl.specter :as sp]

   [reagent.core :as rgt]
   [reagent.dom :as rgtd]

   [cljsjs.codemirror]
   [cljsjs.codemirror.mode.clojure]
   [cljsjs.codemirror.mode.python]
   [cljsjs.codemirror.mode.javascript]
   [cljsjs.codemirror.addon.hint.show-hint]

   [cljsjs.codemirror.addon.comment.comment]
   [cljsjs.codemirror.addon.dialog.dialog]
   [cljsjs.codemirror.addon.display.panel]

   [cljsjs.codemirror.addon.search.search]
   [cljsjs.codemirror.addon.search.searchcursor]
   [cljsjs.codemirror.addon.search.jump-to-line]

   [cljsjs.codemirror.addon.edit.closebrackets]
   [cljsjs.codemirror.addon.edit.matchbrackets]

   [cljsjs.codemirror.keymap.emacs]
   [cljsjs.codemirror.keymap.sublime]
   [cljsjs.codemirror.keymap.vim]

   [cljsjs.highlight]
   [cljsjs.highlight.langs.clojure]
   [cljsjs.highlight.langs.javascript]
   [cljsjs.highlight.langs.python]

   [aerial.saite.cmemacs :as em]
   [paredit-cm.core :as pe]

   [re-com.core
    :as rcm
    :refer [h-box v-box box border gap line h-split v-split scroller
            button row-button md-icon-button md-circle-icon-button info-button
            input-text input-password input-textarea
            label title p
            single-dropdown
            checkbox radio-button slider progress-bar throbber
            horizontal-bar-tabs vertical-bar-tabs
            modal-panel popover-content-wrapper popover-anchor-wrapper]
    :refer-macros [handler-fn]]
   [re-com.box
    :refer [flex-child-style]]
   [re-com.dropdown
    :refer [filter-choices-by-keyword single-dropdown-args-desc]]

   ))



(defn init []
  (load-analysis-cache!))




(defn cminsert
  "Modified version of pe/insert which does not understand multiline text."
  ([cm text] (cminsert cm text 0))
  ([cm text offset] (cminsert cm text offset (pe/cursor cm)))
  ([cm text offset cur]
   (let [i (pe/index cm cur)]
     (.replaceRange cm text cur)
     (.setCursor cm (pe/cursor cm (+ i (count text) offset)))
     (pe/cursor cm))))

(defn get-cur-src []
  (let [tid (hmi/get-cur-tab :id)
        eid (get-ddb [:tabs :extns tid :eid])
        cm (get-ddb [:tabs :cms tid eid :$ed])]
    (.getValue cm)))

(defn insert-src-cur [srctxt]
  (let [tid (hmi/get-cur-tab :id)
        eid (get-ddb [:tabs :extns tid :eid])
        cm (get-ddb [:tabs :cms tid eid :$ed])
        offset (- (count srctxt))]
    (cminsert cm srctxt offset)))


(defn eval-code-after-load []
  (on-load-eval))




(defn get-cm-sexpr [cm]
  (let [end (.getCursor cm)
        start (do (em/backward-sexp cm)
                  (.getCursor cm))
        stgval (.getValue cm)
        lines (subvec (clojure.string/split-lines stgval)
                      start.line (inc end.line))
        begstg (-> lines first (subs start.ch))
        endstg (-> lines last (subs 0 end.ch))]
    (.setCursor cm end)
    (if (= start.line end.line)
      (let [l (first lines)]
        (subs l start.ch end.ch))
      (let [midstg (clojure.string/join
                    "\n" (subvec lines 1 (dec (count lines))))]
        (clojure.string/join "\n" [begstg midstg endstg])))))

(defn find-outer-sexpr [cm]
  (let [f em/backward-up-list
        b em/backward-sexp
        startpos (.getCursor cm)]
    (loop [pos (do (f cm) (.getCursor cm))
           lastpos startpos
           cnt 20] ; crazy big guard
      #_(js/console.log pos)
      (cond
        (<= cnt 0) pos
        (= 0 pos.ch) pos
        (= lastpos pos) (recur (do (b cm) (.getCursor cm)) pos (dec cnt))
        :else (recur (do (f cm) (.getCursor cm)) pos (dec cnt))))))

(defn get-outer-sexpr-src [cm]
  (let [pos (.getCursor cm)
        _ (find-outer-sexpr cm)
        _ (em/forward-sexp  cm)
        s (get-cm-sexpr cm)]
    (.setCursor cm pos)
    s))


(def LIST-NODES
  (sp/recursive-path
   [] p
   (sp/if-path
    ;;list?
    #(or (list? %) (vector? %))
    (sp/continue-then-stay sp/ALL p))))




(defn get-cm-frame-ids [cm]
  (let [l (get-all-cm-as-code cm)]
    (->> l (sp/select [LIST-NODES #(= (first %) 'hc/xform)])
         (mapv (fn[fm] (->> fm (drop-while #(not= % :FID)) second)))
         (filter identity))))

(defn get-tab-specs [tid]
  (sp/select-one [sp/ATOM :tabs :active sp/ATOM sp/ALL
                  #(= (% :id) tid) :specs]
                 hmi/app-db))


(defn get-tab-frame-ids [tid]
  (mapv #(-> % :usermeta :frame :fid) (get-tab-specs tid)))

(defn frame-exists? [fid]
  (let [tid (hmi/get-cur-tab :id)]
    (some #{fid} (get-tab-frame-ids tid))))

(defn get-frame-index [cm]
  (->> cm get-outer-sexpr-src read-string
       (sp/select [LIST-NODES #(= (first %) 'hc/xform)])
       last (drop-while #(not= % :FID)) second))

(defn get-frame-index-positions [cm]
  (let [tid (hmi/get-cur-tab :id)
        fid (get-frame-index cm)
        tab-fids (get-tab-frame-ids tid)
        cm-fids (get-cm-frame-ids cm)]
    {:fid fid
     :tab-pos (keep-indexed #(when (= fid %2) %1) tab-fids)
     :cm-pos  (keep-indexed #(when (= fid %2) %1) cm-fids)
     :tab-fids tab-fids
     :cm-fids cm-fids}))

(defn current-cm-frame-info [cm]
  (let [fidinfo (get-frame-index-positions cm)
        fid (fidinfo :fid)
        cm-fids (fidinfo :cm-fids)
        tab-fids (fidinfo :tab-fids)
        b4ids (->> cm-fids (take-while #(not= % fid)) (into #{}))
        tb4ids (keep #(b4ids %) tab-fids)
        locid (cond (not (seq b4ids)) :beg
                    (seq tb4ids) (last tb4ids)
                    :else :end)]
    {:fid fid :locid locid
     :tabfid (if (seq tab-fids) (frame-exists? fid) nil)
     :b4ids b4ids :tb4ids tb4ids}))



(defn get-cm-block [cm]
  (let [pos (.getCursor cm)
        line pos.line
        ch pos.ch
        stgval (.getValue cm)
        lines (cljstr/split-lines stgval)
        find-hd (fn [l dir]
                  (let [f (if (= dir :bkwd) dec inc)
                        stop (if (= dir :bkwd) 0 (-> lines count dec))]
                    (loop [l l]
                      (let [line (lines l)]
                        (if (or (= l stop) (cljstr/starts-with? line ";@@@"))
                          [l line]
                          (recur (f l)))))))
        [start l1] (find-hd line :bkwd)
        [end l2]   (find-hd line :fwd)
        block      (->> (subvec lines start end)
                        (filter #(not (cljstr/starts-with? % ";;"))))
        hd (-> block first (cljstr/replace #";@@@[ ]*" ":")
               (cljstr/split #" +"))
        blockstg (cljstr/join "\n" (rest block))]
    #_(println line ch start l1 end l2 hd)
    [hd blockstg]))

(defn get-chunk-code [lines]
  (let [v (atom [])]
    (doseq [l lines]
      (evaluate
       l #(swap! v (fn[v] (conj v (if (% :value) (% :value) %))))))
    @v))


(defmulti gen-block-code
  (fn[hd blines] (first hd)))

(defmethod gen-block-code :MD
  [hd blines]
  (let [id (second hd)
        margin? (cljstr/starts-with? (first blines) ":margin")
        margin (if margin?
                 (-> blines first (cljstr/split #" " ) second)
                 "20px")
        blines (if margin? (rest blines) blines)
        body (cljstr/join "\n" blines)]
    (format "(hmi/add-to-tab-body
              (hmi/get-cur-tab :id)
              (hc/xform
               ht/empty-chart :FID %s
               :TOP '[[gap :size %s]
                      [md %s]]))" id margin body)))

(defn get-body [hdr bodystg]
  (let [[kind id] hdr
        body-lines (cljstr/split-lines bodystg)]
    (gen-block-code hdr body-lines)))

(defn process-cm-block [cm]
  (let [[hd blockstg] (get-cm-block cm)
        hd-code (get-chunk-code hd)
        body (get-body hd-code blockstg)]
    body))





;;; ----------------------------------------------------------------------- ;;;
;;;               Insertion of 'various frame template code'                ;;;
;;; ----------------------------------------------------------------------- ;;;


(defn get-md-defaults
  ([]
   (let [tid (hmi/get-cur-tab :id)
         custopts (or (get-ddb [:tabs :md-defaults tid]) {})
         {:keys [md cm]} (get-ddb [:main :interactive-tab :md-defaults])
         opts {:md (merge md (custopts :md {}))
               :cm (merge cm (custopts :cm {}))}]
     opts))
  ([k] (let [m (get-md-defaults)] (m k {}))))

(defn set-md-defaults [optsmap]
  (let [tid (hmi/get-cur-tab :id)]
    (update-ddb [:tabs :md-defaults tid] optsmap)
    :ok))


(defn next-cmid []
  (let [tid (hmi/get-cur-tab :id)
        cmid (inc (or (get-ddb [:tabs :extns tid :cmfids :cm]) 0))]
    (update-ddb [:tabs :extns tid :cmfids :cm] cmid)
    cmid))

(defn next-fid []
  (let [tid (hmi/get-cur-tab :id)
        fid (inc (or (get-ddb [:tabs :extns tid :cmfids :fm]) 0))]
    (update-ddb [:tabs :extns tid :cmfids :fm] fid)
    (keyword (str "f" fid))))

(defn next-vid []
  (let [tid (hmi/get-cur-tab :id)
        vid (inc (or (get-ddb [:tabs :extns tid :cmfids :vis]) 0))]
    (update-ddb [:tabs :extns tid :cmfids :vis] vid)
    (keyword (str "v" vid))))


(defn insert-cm-md [cm]
  (let [cmmd (format "[cm :id \"cm%s\" :fid :FID :src \" \"]" (next-cmid))]
    (cminsert cm cmmd -2)))

(defn insert-md [cm]
  (let [{:keys [width font-size]} (get-md-defaults :md)
        mdfm (format "[md {:style {:font-size \"%s\" :width \"%s\"}}
\"

\"]"
                     font-size width)]
    (cminsert cm mdfm -3)))


(defn insert-txt-frame [cm]
  (let [{:keys [vmargin margin width font-size]} (get-md-defaults :md)
        form
        (format "
(hc/xform
 ht/empty-chart
 :FID %s
 :LEFT '[[p {:style {:width \"%s\" :min-width \"%s\"}}]]
 :TOP '[[gap :size \"%s\"]
        [v-box :gap \"10px\"
         :children
         [[md {:style {:font-size \"%s\" :width \"%s\"}}
\"

\"]

]]])"
                (next-fid) vmargin vmargin margin font-size width)]
    (cminsert cm form -9)))


(defn insert-vis-frame [cm]
  (let [{:keys [vmargin margin width font-size]} (get-md-defaults :md)
        form
        (format "
(hc/xform
 ht/point-chart
 :FID %s :VID %s
 :DATA (mapv (fn[i] {:x i :y i}) (range 50)) ; change to your data source
 :LEFT '[[p {:style {:width \"%s\" :min-width \"%s\"}}]]
 :TOP '[[gap :size \"%s\"]
        ])"
                (next-fid) (next-vid) vmargin vmargin margin)]
    (cminsert cm form -162)))



;;; ----------------------------------------------------------------------- ;;;
;;;               Document frame insertion, deletion, edit                  ;;;
;;; ----------------------------------------------------------------------- ;;;

(def dbg-cm (atom nil))


(declare eval-mixed-cc)

(defn make-error-frame [fid msg]
  {:usermeta
   {:frame {:fid (if fid fid (keyword (gensym "ERR")))
            :top [[p {:style {:font-size "20px" :color "red"}} msg]]}}})

(defn insert-handle-return [cm res] (printchan :RES res)
  (let [tid (hmi/get-cur-tab :id)
        nssym (get (get-ddb [:tabs :extns tid]) :ns 'aerial.saite.compiler)
        {:keys [fid locid tabfid]} (current-cm-frame-info cm)
        pos (if (= locid :beg) :same :after)
        e (js->clj (res :error))
        err (cond e e.cause.message
                  (= (res :value) hc/xform) "Error : Missing closing paren!"
                  :else nil)
        undefined (when err (re-find #"undefined" err))
        errmsg (if undefined
                 (str err "'undefined' is usually due to undeclared var")
                 err)
        value (res :value)
        picframe (if err (make-error-frame fid errmsg) value)]
      #_(printchan :IF fid err tabfid)
      (when (or fid err)
        (if (not tabfid)
          (tops/add-frame picframe locid pos)
          (tops/update-frame :frame picframe)))))

(defn insert-frame [cm]
  (go
    (let [resch (chan)
          _ (eval-mixed-cc cm :cb (fn[v] (go (async/>! resch v))))
          res (async/<! resch)]
      (if (instance? js/Promise (res :value))
        (.then (res :value) (fn[val] (insert-handle-return cm {:value val})))
        (insert-handle-return cm res)))))

(defn delete-frame [cm]
  (let [{:keys [fid locid tabfid]} (current-cm-frame-info cm)]
    (when (and fid tabfid)
      (tops/remove-frame tabfid))))

(defn re-visualize [cm]
  (let [tid (hmi/get-cur-tab :id)
        nssym (get (get-ddb [:tabs :extns tid]) :ns 'aerial.saite.compiler)
        {:keys [fid locid tabfid]} (current-cm-frame-info cm)
        src (get-outer-sexpr-src cm)
        res (volatile! nil)
        _ (evaluate nssym src (fn[v] (vswap! res #(do v))))]
    (when (and (@res :value) fid tabfid)
      (tops/update-frame :vis (@res :value)))))


(defn doctab? []
  (let [tid (hmi/get-cur-tab :id)
        opts  (hmi/get-tab-field tid :opts)]
    (opts :rgap)))

(defn enhanced-cut [cm]
  (if (not (doctab?))
    (em/kill-region cm)
    (let [ctrlwfn em/kill-region
          start (.getCursor cm "start")
          end (.getCursor cm "end")
          stgval (.getValue cm)
          lines (clojure.string/split-lines stgval)
          lines (subvec lines start.line (inc end.line))
          cnt (count lines)
          first-line (-> lines first (subs start.ch))
          last-line (if (= 1 cnt) "" (-> lines last (subs 0 end.ch)))
          mid-lines (if (= 1 cnt) [] (subvec lines 1 (dec (count lines))))
          lines (-> [first-line mid-lines last-line] flatten vec)
          src (clojure.string/join "\n" lines)
          fm (try (read-string src) (catch js/Error e []))
          fid (when (seq? fm) (->> fm (drop-while #(not= % :FID)) second))]
      (when fid (tops/remove-frame fid))
      (ctrlwfn cm))))

(defn enhanced-yank [cm]
  (let [ctrlyfn em/yank
        bsexpfn em/backward-sexp
        _ (ctrlyfn cm)
        pos (.getCursor cm)]
    (when (doctab?)
      (bsexpfn cm)
      (go (let [ch (insert-frame cm)]
            (async/<! ch)
            (.setCursor cm pos))))))




;;; ----------------------------------------------------------------------- ;;;
;;;               Code evaluation - client, server, mixed                   ;;;
;;; ----------------------------------------------------------------------- ;;;

;;#(reset! expr* %)
(defn evalxe [cm] (reset! dbg-cm cm)
  (let [cb cm.CB
        tid (hmi/get-cur-tab :id)
        nssym (get (get-ddb [:tabs :extns tid]) :ns 'aerial.saite.compiler)]
    (if-let [source (get-cm-sexpr cm)]
      (evaluate nssym source cb)
      (cb {:value "not on sexpr"}))))

(defn evalcc [cm] (reset! dbg-cm cm)
  (let [cb cm.CB
        tid (hmi/get-cur-tab :id)
        nssym (get (get-ddb [:tabs :extns tid]) :ns 'aerial.saite.compiler)
        src (get-outer-sexpr-src cm)]
    (evaluate nssym src cb)))




;;; Server side execution
;;;
(defn eval-inner-on-jvm [nssym code tid eid]
  (let [src (if (string? code) code (prn-str code))
        ch (async/chan)
        chankey (keyword (gensym "chan-"))
        res (volatile! nil)
        spinr (get-ddb [:editors tid eid :opts :throbber])]
    (update-ddb [:main :chans chankey] ch)
    (hmi/send-msg {:op :eval-clj
                   :data {:uid (hmi/get-adb [:main :uid])
                          :eval true
                          :chankey chankey
                          :nssym nssym
                          :code src}})
    (reset! spinr true)
    ch))

(defn selfhost-eval-on-jvm [nssym code tid eid]
  (js/Promise.
   (fn[resolve reject]
     (let [src (if (string? code) code (prn-str code))
           ch (async/chan)
           chankey (keyword (gensym "chan-"))
           spinr (or (get-ddb [:editors tid eid :opts :throbber]) (atom false))]
       (update-ddb [:main :chans chankey] ch)
       (hmi/send-msg {:op :eval-clj
                      :data {:uid (hmi/get-adb [:main :uid])
                             :eval true
                             :chankey chankey
                             :nssym nssym
                             :code src}})
       (reset! spinr true)
       (go (let [data (async/<! ch)]
             (update-ddb [:main :chans chankey] :rm)
             (reset! spinr false)
             (resolve (data :value))))))))




(defn get-mixed-cc [cm]
  (let [cb cm.CB
        src (get-outer-sexpr-src cm)
        tid (hmi/get-cur-tab :id)
        nssym (get (get-ddb [:tabs :extns tid]) :ns 'aerial.saite.compiler)
        eid (get-ddb [:tabs :extns tid :eid])
        clj? (volatile! false)
        clj-data (atom {:nssym nssym})
        srccd (read-string src)
        srccd (if (and (list? srccd) (= (first srccd)))
                (list 'do srccd)
                srccd)
        code (xform-clj clj? srccd clj-data)
        submap {:aerial.hanami.common/use-defaults? false
                :aerial.hanami.common/spec {}
                :body code
                :tail 'res}
        code (hc/xform '(let [$state$ (volatile! {})] :body) submap)
        code (if @clj?
               [nssym @clj-data code]
               [nssym nil code])]
    code))

(defn eval-mixed-cc [cm & {:keys [cb]}]
  (let [cb (if cb cb cm.CB)
        tid cm.TID
        [nssym clj-data cljscode] (get-mixed-cc cm)
        cljfms (and clj-data (clj-data :fms))]
    #_(printchan :CLJSCODE cljscode)
    (cond
      (and cljscode cljfms)
      (evaluate nssym cljscode cb)

      cljscode
      (evaluate nssym cljscode cb)

      cljfms
      (do
        (update-ddb [:alert :txt]
                    "Error : Clojure only in mixed request")
        (reset! (get-ddb [:alert :show?]) true))

      :else
      (do
        (update-ddb [:alert :txt]
                    "Error : Unable to determine or unknown code sequence")
        (reset! (get-ddb [:alert :show?]) true)))))




(defn xform-code []
  (let [tid (hmi/get-cur-tab :id)
        eid (get-ddb [:tabs :extns tid :eid])
        cm (get-ddb [:tabs :cms tid eid :$ed])
        cb cm.CB
        [nssym clj-data cljscode] (get-mixed-cc cm)]
    (cb {:value cljscode})))




(defn evaljvm-xe [cm]
  (let [cb cm.CB
        src (get-cm-sexpr cm)]
    (if src
      (eval-on-jvm src cb)
      (cb {:value "not on sexpr"}))))

(defn evaljvm-cc [cm]
  (let [cb cm.CB
        src (get-outer-sexpr-src cm)]
    (eval-on-jvm src cb)))




;;; ----------------------------------------------------------------------- ;;;
;;;                       Misc. Editor Support                              ;;;
;;; ----------------------------------------------------------------------- ;;;

(defn clear-output [cm]
  (let [;;eid (get-ddb [:tabs :extns (hmi/get-cur-tab :id) :eid])
        tid cm.TID
        eid cm.EID
        output (get-ddb [:editors tid eid :ot])]
    (when output
      (reset! output ""))))


(defn get-sym-at-cursor [cm]
  (try
    (let [sym (get-cm-sexpr cm)
          sym? (-> sym read-string symbol?)]
      [sym sym?])
    (catch js/Error e
      (js/alert (str "Cannot read symbol\n\n" e)))))

(defn show-doc [cm]
  (let [[sym sym?] (get-sym-at-cursor cm)
        cb cm.CB]
    (if sym?
      (eval-on-jvm (format "(with-out-str (cr/doc %s))" sym) cb)
      "Cursor not on symbol")))

(defn show-source [cm]
  (let [[sym sym?] (get-sym-at-cursor cm)
        cb cm.CB]
    (if sym?
      (eval-on-jvm (format "(with-out-str (cr/source %s))" sym) cb)
      "Cursor not on symbol")))


(defn center-pos [cm]
  (let [pos (.getCursor cm)
        se (.getScrollerElement cm)
        midh (/ se.offsetHeight 2)
        coords (.charCoords cm pos "local")
        top coords.top]
    #_(console.log top midh)
    (.scrollTo cm nil (- top midh 5))))

(defn recenter-top-bottom [cm]
  (center-pos cm))




;;; ----------------------------------------------------------------------- ;;;
;;;                       Key Binding Support                               ;;;
;;; ----------------------------------------------------------------------- ;;;

(def xtra-key-xref
  (->>
   (mapv vector
         '[pe/forward-sexp pe/backward-sexp
           pe/splice-sexp
           pe/splice-sexp-killing-backward pe/splice-sexp-killing-forward
           pe/raise-sexp
           pe/forward-slurp-sexp pe/forward-barf-sexp
           pe/backward-slurp-sexp pe/backward-barf-sexp
           pe/split-sexp pe/join-sexps
           pe/reindent-defun
           enhanced-cut enhanced-yank
           insert-frame delete-frame re-visualize
           xform-code evalxe evalcc eval-mixed-cc
           evaljvm-xe evaljvm-cc
           clear-output show-doc show-source recenter-top-bottom
           insert-cm-md insert-md insert-txt-frame insert-vis-frame]
         [pe/forward-sexp pe/backward-sexp
          pe/splice-sexp
          pe/splice-sexp-killing-backward pe/splice-sexp-killing-forward
          pe/raise-sexp
          pe/forward-slurp-sexp pe/forward-barf-sexp
          pe/backward-slurp-sexp pe/backward-barf-sexp
          pe/split-sexp pe/join-sexps
          pe/reindent-defun
          enhanced-cut enhanced-yank
          insert-frame delete-frame re-visualize
          xform-code evalxe evalcc eval-mixed-cc
          evaljvm-xe evaljvm-cc
          clear-output show-doc show-source recenter-top-bottom
          insert-cm-md insert-md insert-txt-frame insert-vis-frame])
   (into {})))

(defn xform-kb-syms [kb-map]
  (->> kb-map
       (mapv (fn[[k sym]]
               [k (or (xtra-key-xref sym)
                      (em/emacs-kb-xref (-> sym name symbol)))]))
       (into {})))

(defn xtra-keys-emacs-old []
  (CodeMirror.normalizeKeyMap
   (js->clj {"Ctrl-F"         pe/forward-sexp
             "Ctrl-B"         pe/backward-sexp
             "Ctrl-Left"      pe/forward-barf-sexp
             "Ctrl-Right"     pe/forward-slurp-sexp
             "Ctrl-Alt-Left"  pe/backward-barf-sexp
             "Ctrl-Alt-Right" pe/backward-slurp-sexp

             "Ctrl-Home"      em/go-doc-start
             "Ctrl-End"       em/go-doc-end
             "Ctrl-X D"       show-doc
             "Ctrl-X S"       show-source

             "Alt-W"          enhanced-cut
             "Ctrl-Y"         enhanced-yank
             "Alt-K"          em/kill-sexp
             "Ctrl-X R"       em/query-replace
             "Ctrl-X Ctrl-B"  clear-output

             "Ctrl-Alt-W"     enhanced-cut
             "Ctrl-Alt-Y"     enhanced-yank
             "Ctrl-X Ctrl-I"  insert-frame
             "Insert"         insert-frame
             "Ctrl-X Ctrl-D"  delete-frame
             "Delete"         delete-frame
             "Ctrl-X Ctrl-V"  re-visualize

             "Ctrl-X X"       xform-code
             "Ctrl-X Ctrl-E"  evalxe
             "Ctrl-X Ctrl-C"  eval-mixed-cc ;evalcc
             "Ctrl-X J"       evaljvm-xe
             "Ctrl-X Ctrl-J"  evaljvm-cc
             "Ctrl-X Ctrl-M"  eval-mixed-cc
             })))

(defn xtra-keys-emacs []
  (CodeMirror.normalizeKeyMap
   (js->clj
    (get-ddb [:main :editor :key-bindings]))))




;;; ----------------------------------------------------------------------- ;;;
;;;                CodeMirror Reagent/React Component Support               ;;;
;;; ----------------------------------------------------------------------- ;;;

(defn code-mirror
  "Create a code-mirror editor. The parameters:
  value-atom (reagent atom)
    when this changes, the editor will update to reflect it.
  options
  :js-cm-opts
    options passed into the CodeMirror constructor"
  [input mode
   & {:keys [js-cm-opts cb tid eid ed? tbody]
      :or {cb #(printchan %)}}]
  (printchan "CODE-MIRROR called")
  (let [cm (atom nil)
        curpos (when ed? (get-ddb [:editors tid eid :opts :curpos]))]
    (rgt/create-class
     {:display-name "CMirror"

      :component-did-mount
      (fn [comp]
        (printchan "CM did-mount called")
        #_(js/console.log comp, (rgtd/dom-node comp))
        (let [opts (clj->js (merge
                             {:lineNumbers true
                              :lineWrapping true,
                              :viewportMargin js/Infinity
                              :autofocus true
                              :keyMap (get-ddb [:main :editor :name])
                              :extraKeys (xtra-keys-emacs)
                              :matchBrackets true
                              :autoCloseBrackets true
                              :value @input
                              :mode mode}
                             js-cm-opts))
              ;;pos (get-ddb [:editors tid eid :opts :curpos])
              inst (.fromTextArea js/CodeMirror (rgtd/dom-node comp) opts)]

          (.setValue inst @input)
          (set! (.-CB inst) cb)
          (set! (.-EID inst) eid)
          (set! (.-TID inst) tid)
          (.setOption inst "theme" (get-ddb [:main :editor :theme]))
          (reset! cm inst)
          (reset! dbg-cm inst)

          (when (not tbody)
            (update-ddb [:tabs :extns tid :cms (if ed? :$ed :$ot)] inst))
          (update-ddb [:tabs :cms tid eid (if ed? :$ed :$ot)] inst)

          (when-let [edcm (get-ddb [:tabs :extns tid :cms :$ed])] (.focus edcm))
          (when ed?
            (.setCursor inst @curpos)
            (center-pos inst))
          (.on inst "change"
               (fn [_ _]
                 (let [value (.getValue inst)]
                   (when (not= value @input) (reset! input value)))))
          (.on inst "cursorActivity"
               (fn [_]
                 (when curpos (reset! curpos (.getCursor @cm)))))
          ))

      :component-did-update
      (fn [comp old-argv]
        (printchan "CM did-update called")
        (when (not= @input (.getValue @cm))
          #_(printchan :INPUT @input :CM (.getValue @cm))
          (.setValue @cm @input)
          ;; reset the cursor to the end of the text, if the text was
          ;; changed externally
          (let [last-line (.lastLine @cm)
                last-ch (count (.getLine @cm last-line))]
            (.setCursor @cm last-line last-ch))))

      :reagent-render
      (fn [_ _ _]
        @input
        [:textarea {:rows "20",
                    #_:style #_"flex: 0 0 auto; padding-right: 12px;"}])})))




(defn set-theme [cm theme]
  ;; editor.setOption("theme", choice)
  (update-ddb [:main :editor :theme] theme)
  (.setOption cm "theme" theme))


(defn spinner??? []
  [box
   :child [:div {:class "lds-spinner"}
           [:div] [:div] [:div] [:div] [:div] [:div]
           [:div] [:div] [:div] [:div] [:div] [:div]]])

(defn spinner []
  [box
   :justify :center
   :child [:div {:class "spinner"}]])

(defn editor-hiccup [opts input output]
  [v-box
   :children
   [[box
     :size (opts :size "auto")
     :width (opts :width "500px")
     :height (opts :height "300px")
     :justify (opts :justify :start)
     :align (opts :justify :stretch)
     :child [code-mirror input (get-ddb [:main :editor :mode])
             :js-cm-opts (when (opts :readonly)
                           {:readOnly (opts :readonly)
                            :lineNumbers false})
             :eid (opts :id)
             :tid (opts :tid)
             :ed? true
             :tbody (opts :tbody)
             :cb (fn[m]
                   (let [oval (or (m :value) (m :error))]
                     (if (instance? js/Promise oval)
                       (.then oval
                              (fn[res]
                                (reset! output
                                        (str @output "=> "
                                             (with-out-str
                                               (cljs.pprint/pprint res))))))

                       (let [ostg (if (and (string? oval)
                                           (re-find #"\n" oval))
                                    oval
                                    (with-out-str
                                      (cljs.pprint/pprint
                                       (or (m :value) (m :error)))))]
                         (reset! output (str @output
                                             "=> " ostg))
                         (printchan :output @output)))))]]]])

(defn output-hiccup [opts oh output]
  [v-box
   :children
   [[box
     :size (opts :size "auto")
     :width (opts :out-width "900px")
     :height oh
     :justify (opts :justify :start)
     :align (opts :justify :stretch)
     :child [code-mirror output (get-ddb [:main :editor :mode])
             :eid (opts :id)
             :tid (opts :tid)
             :tbody (opts :tbody)
             :js-cm-opts {:lineNumbers false,
                          :lineWrapping false,
                          :readOnly (opts :readonly false)}]]]])

(defn cm-hiccup [opts input output]
  (let [id (opts :id)
        kwid (-> id name keyword)
        layout (if (= (opts :layout) :up-down) v-box h-box)
        ed-pos (if (= (opts :ed-out-order) :first-last) :first :last)
        ch (opts :height "400px")
        oh (opts :out-height (cond (opts :readonly) "0px"
                                   (opts :tbody) "50px"
                                   :else "100px"))
        esratom (opts :$esratom)]
    [h-box :gap "5px" :attr {:id id}
     :children
     [[gap :size "3px"]
      (when (not (opts :readonly))
        [v-box :gap "5px"
         :children
         [[md-circle-icon-button
           :md-icon-name "zmdi-circle-o"
           :tooltip "Clear"
           :on-click #(do (reset! output ""))]
          [md-circle-icon-button
           :md-icon-name "zmdi-refresh" ;"zmdi-replay"
           :tooltip "Rerun"
           :on-click #(do (eval-all))]
          (when (deref (opts :throbber))
            [spinner])]])
      [layout :gap "5px"
       :width (opts :width "500px")
       :height (+ ch oh 50)
       :children
       (cond
         (#{0 "0" "0px"} oh)
         [[editor-hiccup opts input output]]
         (= ed-pos :first)
         [[editor-hiccup opts input output]
          [output-hiccup opts oh output]]
         :else
         [[output-hiccup opts oh output]
          [editor-hiccup opts input output]])]
      [gap :size "10px"]]]))


(defn cm []
  (let [input (rgt/atom "")
        output (rgt/atom "")
        pos (atom (new js/CodeMirror.Pos 0 0 "after"))]
    (fn [& opts]
      (let [opts (->> opts (partition-all 2) (mapv vec) (into {}))
            kwid (name (opts :id (gensym "cm-")))
            tid (opts :tid)
            path [:editors tid kwid]
            opts (or (get-ddb (conj path :opts))
                     (merge {:id kwid, :size "auto"
                             :layout :up-down :ed-out-order :last-first
                             :height "300px", :out-height "100px"
                             :$esplit 0.2
                             :curpos pos
                             :throbber (rgt/atom false)}
                            opts))
            _ (if (and (opts :src) (= @input "")) (reset! input (opts :src)))
            input (or (get-ddb (conj path :in)) input)
            output (or (get-ddb (conj path :ot)) output)]
        (when-not (get-ddb path)
          (assoc opts :$esratom (rgt/atom (opts :$esplit)))
          (update-ddb path {:in input, :ot output, :opts opts})
          (printchan :CM kwid :called :OPTS opts))
        [cm-hiccup opts input output]))))




(comment


  (defn vis! [vid picid]
    (hmi/visualize
     (get-vspec vid)
     (js/document.getElementById picid)))

  (vis! :bc1 "scatter-1")
  (vis! :sqrt "scatter-1")
  (vis! :scatter-1 "scatter-1")

  (when-let [source (get-cm-sexpr @dbg-cm)]
    (evaluate source #(printchan (expr*! %))))

  (js/console.log (.setCursor @dbg-cm 2 42))
  (js/console.log (.getCursor @dbg-cm))
  (js/console.log (.findMatchingBracket @dbg-cm (.getCursor @dbg-cm)))





  (let [cm @dbg-cm
        cb cm.CB]
    (evaluate (process-cm-block cm) cb))

  (let [src (prn-str (xform-tab-data (get-tab-data)))
        res (atom {})]
    (evaluate src (fn[x] (reset! res x)))
    (if-let [v (:value @res)]
      v
      @res))

  (let [src (prn-str (get-tab-data))
        res (atom {})]
    (evaluate src (fn[x] (reset! res  (x :value))))
    (get-in @res [0 :chap1 :opts :wrapfn :fn]))





  (if-let [bounds (.findMatchingBracket @dbg-cm (.getCursor @dbg-cm))]
    (.setCursor @dbg-cm bounds.to))

  (if-let [bounds (.findMatchingBracket @dbg-cm (.getCursor @dbg-cm))]
    (let [start bounds.to
          end bounds.from
          stgval (.getValue @dbg-cm)
          lines (subvec (clojure.string/split-lines stgval)
                        start.line (inc end.line))
          begstg (-> lines first (subs start.ch))
          endstg (-> lines last (subs 0 (inc end.ch)))]
      (if (= start.line end.line)
        (subs begstg 0 end.ch)
        (let [midstg (clojure.string/join
                      " " (subvec lines 1 (dec (count lines))))]
          (clojure.string/join " " [begstg midstg endstg]))))
    "cursor not at sexpr end")

  js/CodeMirror.keyNames
  js/CodeMirror.keyMap
  js/CodeMirror.keyMap.emacs

  )
