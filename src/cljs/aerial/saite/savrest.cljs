(ns aerial.saite.savrest
  (:require

   [clojure.string :as cljstr]
   [clojure.set :refer [map-invert]]

   [aerial.hanasu.common
    :refer [update-db get-db]]
   [aerial.hanami.core
    :as hmi
    :refer [printchan]]

   [reagent.core :as rgt]
   [com.rpl.specter :as sp]

   [cljsjs.jszip :as jsz]
   [cljsjs.filesaverjs]
   ))




;;; Data DB =============================================================== ;;;


(defonce data-db (atom {}))

(defn update-ddb
  ([] (update-db data-db {}))
  ([keypath vorf]
   (update-db data-db keypath vorf))
  ([kp1 vof1 kp2 vof2 & kps-vs]
   (apply update-db data-db kp1 vof1 kp2 vof2 kps-vs)))

(defn get-ddb
  ([] (get-db data-db []))
  ([key-path]
   (get-db data-db key-path)))


(def symxlate-cb-map (atom {:vars {} :fns {}}))

(defn add-symxlate [sym val]
  (let [k (-> sym name keyword)
        kfn (fn [& args]
              (let [thefn (get-in @symxlate-cb-map [:fns k])]
                (apply thefn args)))]
    (when-not (get-in @symxlate-cb-map [:vars (name sym)])
      (swap! symxlate-cb-map #(assoc-in % [:vars (name sym)] kfn)))
    (swap! symxlate-cb-map #(assoc-in % [:fns k] val))))

(defn get-symxlate [sym]
  (get-in @symxlate-cb-map [:vars (name sym)] sym))


(def ratom-map (atom {}))

(defn add-ratom [id val]
  (let [k (if (keyword? id) id (-> id str keyword))
        ratom  (get @ratom-map k (rgt/atom nil))]
    (reset! ratom val)
    (swap! ratom-map #(assoc % k ratom ratom k))
    ratom))

(defn get-ratom [id]
  (let [k (if (or (keyword? id)
                  (instance? reagent.ratom/RAtom id))
            id
            (-> id str keyword))]
    (get @ratom-map k)))



;;; Static chart saving =================================================== ;;;

#_(require '[cljsjs.jszip :as jsz])
#_(require  'cljsjs.filesaverjs)

#_(-> (.toImageURL (hmi/get-vgview :scatter-1) "png")
      (.then (fn[url]
               (let [link (js/document.createElement "a")]
                 (.setAttribute link "href" url)
                 (.setAttribute link "target" "_blank")
                 (.setAttribute link "download" "My-Scatter-1.png")
                 (.dispatchEvent link (new js/MouseEvent "click")))))
      (.catch (fn[err] (printchan err))))


(defn zipvis [tabvgviews views zip dir zipfolder]
  (cond
    (and (not (seq tabvgviews))
         (not (seq views)))
    (let [archive-name (-> zipfolder (cljstr/split #"/") vec
                           (conj "Charts.zip")
                           (->> (cljstr/join "-")))]
      (-> (.generateAsync zip #js{:type "blob"})
          (.then (fn[blob]
                   (js/saveAs blob archive-name)
                   (hmi/send-msg {:op :save-charts
                                  :data {:uid (hmi/get-adb [:main :uid])
                                         :archive archive-name}})))
          (.catch (fn[err] (printchan err)))))

    (not (seq views))
    (let [[tid views] (first tabvgviews)]
      (zipvis (rest tabvgviews) views zip
              (str zipfolder "/" (name tid)) zipfolder))

    :else
    (let [[nm view] (first views)]
      (-> (.toImageURL view "png")
          (.then (fn[url]
                   (.file zip
                          (str dir "/" (name nm) ".png")
                          (.slice url 22)
                          #js{:base64 true})
                   (zipvis tabvgviews (rest views) zip
                           dir zipfolder)))))))

(defn gen-chart-zip []
  (let [dir (get-ddb [:main :docs :dir])
        file (get-ddb [:main :docs :load])
        archive-name (cljstr/join "-" [dir file "Charts.zip"])
        zipfolder (str dir "/" file)
        tab-vgviews (hmi/get-vgviews)
        zip (new js/JSZip)]
    #_(.folder zip (str dir "/" file))
    (zipvis tab-vgviews [] zip "" zipfolder)))




;;; Document Save and Restore ============================================= ;;;

(defn tab-data []
  (->> (sp/select [sp/ATOM :tabs :active sp/ATOM sp/ALL]
                  hmi/app-db)
       (map (fn[tab]
              (select-keys tab [:id :label :opts :specs])))))

(defn get-extn-info [tid]
  (let [m (get-ddb [:tabs :extns tid])
        m (dissoc m :cms) ; editor object instances can't be saved!!
        md-defaults (get-ddb [:tabs :md-defaults tid])
        m (assoc m :md-defaults md-defaults)
        eid (m :eid)
        src (get-ddb [:editors tid eid :in])
        src (if src (deref src) (m :src))
        sratom (m :$sratom)
        sratom-val (when sratom (deref sratom))
        $split (m :$split)
        $split (if sratom-val sratom-val $split)
        m (-> m (dissoc :$sratom) (assoc :$split $split :src src))]
    (printchan :TID tid :EID eid)
    m))

(defn out-xform-recom-specs [specs recom-syms]
  (sp/transform
   sp/ALL
   (fn[v]
     (cond (and (vector? v)
                (->> v first recom-syms)
                ((set v) :model))
           (let [[i _] (sp/select-one
                        [sp/INDEXED-VALS #(-> % second (= :model))]
                        v)
                 modval (sp/select-one [(sp/nthpath (inc i))] v)
                 modval (if (or (= reagent.ratom/RAtom (type modval))
                                (= cljs.core/Atom (type modval)))
                          {:RATOM @modval :id (get-ratom modval)}
                          modval)
                 v (sp/setval [(sp/nthpath (inc i))] modval v)]
             v)

           (coll? v) (out-xform-recom-specs v recom-syms)
           :else v))
   specs))

(def CM-NODES
  (sp/recursive-path
   [] p
   (sp/cond-path
    map? [sp/ALL p]
    list? [sp/ALL p]
    #(and (vector? %)
          (= (-> (nth % 0) str) "cm")) (sp/continue-then-stay sp/ALL p)
    vector? [sp/ALL p])))

(defn xform-cm-src [tid specs]
  (let [editors (get-ddb [:editors tid])]
    (if (not (seq specs))
      specs
      (sp/transform
       CM-NODES
       (fn[v]
         (let [m (->> v rest (partition-all 2)
                      (mapv vec) (into {}))
               newsrc (-> (m :id) editors :in)
               newsrc (if newsrc (deref newsrc) (m :src))]
           (-> (->> (assoc m :src newsrc)
                    (map identity)
                    flatten)
               (conj (first v))
               vec)))
       specs))))

(defn get-tab-data []
  (let [recom-syms (-> hmi/re-com-xref keys set)]
    (->> (tab-data)
         rest
         (map (fn[m]
                (let [tid (m :id)]
                  {tid (->> [:label :opts :specs]
                            (mapv (fn[k]
                                    (let [v (m k)
                                          v (cond
                                              (and (= k :opts) (v :wrapfn))
                                              (assoc v :wrapfn
                                                     (get-extn-info tid))

                                              (= k :specs)
                                              (vec (out-xform-recom-specs
                                                    (xform-cm-src tid v)
                                                    recom-syms))

                                              :else v)]
                                      (vector k v))))
                            (into {}))})))
         (cons (hmi/get-adb [:main :uid :name]))
         vec)))

(def invert-re-com-xref
  (map-invert hmi/re-com-xref))

(defn xform-tab-data
  [x]
  (sp/transform
   sp/ALL
   (fn[v]
     (cond
       (coll? v) (xform-tab-data v)
       (symbol? v) (-> v name symbol)
       :else (get invert-re-com-xref v v)))
   x))



(defn in-xform-recom-specs [specs recom-syms]
  (sp/transform
   sp/ALL
   (fn[v]
     (cond (and (vector? v)
                (->> v first recom-syms))

           (do
             (when ((set v) :on-change)
               (let [[i _] (sp/select-one
                            [sp/INDEXED-VALS #(-> % second (= :on-change))]
                            v)
                     updtfnsym (sp/select-one [(sp/nthpath (inc i))] v)
                     symfn (get-symxlate updtfnsym)
                     xfn (fn[& a]
                           (js/alert
                            (str ":on-change value "
                                 updtfnsym
                                 " not registered")))]
                 (when-not (fn? symfn) (add-symxlate updtfnsym xfn))))

             (when ((set v) :on-click)
               (let [[i _] (sp/select-one
                            [sp/INDEXED-VALS #(-> % second (= :on-click))]
                            v)
                     updtfnsym (sp/select-one [(sp/nthpath (inc i))] v)
                     symfn (get-symxlate updtfnsym)
                     xfn (fn[& a]
                           (js/alert
                            (str ":on-click value "
                                 updtfnsym
                                 " not registered")))]
                 (when-not (fn? symfn) (add-symxlate updtfnsym xfn))))

             (if ((set v) :model)
               (let [_ (printchan "MODEL" v)
                     [i _] (sp/select-one
                            [sp/INDEXED-VALS #(-> % second (= :model))]
                            v)
                     modval (sp/select-one [(sp/nthpath (inc i))] v)
                     _ (js/console.log i "MODVAL" modval (map? modval))
                     modval (if (and (map? modval) (modval :RATOM))
                              (add-ratom (modval :id) (modval :RATOM))
                              modval)
                     v (sp/setval [(sp/nthpath (inc i))] modval v)]
                 (printchan "MODEL AFTER" v)
                 v)
               (in-xform-recom-specs v recom-syms)))

           (coll? v) (in-xform-recom-specs v recom-syms)

           :else v))
   specs))

(defn load-doc [doc-data extns-xref]
  (let [tids (rest (mapv :id (tab-data)))
        $split (get-ddb [:tabs :extns :$split])
        recom-syms (-> hmi/re-com-xref keys set)]
    (doseq [tid tids] (hmi/del-tab tid))
    (hmi/del-vgviews)
    (update-ddb [:editors] {}
                [:main :chans] {}
                [:tabs] {:extns {:$split $split}})
    (->> doc-data
         (mapv (fn[m]
                 (let [tm (->> m vals first)]
                   (if-let [info (->> tm :opts :wrapfn)]
                     (let [f (-> info :fn second extns-xref)
                           {:keys [tid src]} info
                           oopts (dissoc (tm :opts) :wrapfn)
                           oopts (merge {:rgap "20px" :cgap "20px"
                                         :ed-out-order :first-last}
                                        oopts)
                           label (tm :label)
                           specs (in-xform-recom-specs (tm :specs) recom-syms)
                           args (->> (merge oopts (dissoc info :fn :tid :src))
                                     seq (cons [:specs specs])
                                     (apply concat))]
                       (printchan :ARGS oopts)
                       (apply f tid label src args)
                       [tid :done])
                     (do (-> m vals first :specs
                             (in-xform-recom-specs recom-syms)
                             hmi/update-tabs)
                         [(-> m keys first) :done]))))))))
