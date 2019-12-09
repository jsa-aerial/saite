(ns aerial.saite.savrest
  (:require

   [clojure.set :refer [map-invert]]

   [aerial.hanasu.common
    :refer [update-db get-db]]
   [aerial.hanami.core
    :as hmi
    :refer [printchan]]

   [com.rpl.specter :as sp]
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




;;; Document Save and Restore ============================================= ;;;

(defn tab-data []
  (->> (sp/select [sp/ATOM :tabs :active sp/ATOM sp/ALL]
                  hmi/app-db)
       (map (fn[tab]
              (select-keys tab [:id :label :opts :specs])))))

(defn get-extn-info [tid]
  (let [m (get-ddb [:tabs :extns tid])
        eid (m :eid)
        src (get-ddb [:editors eid :in])
        src (if src (deref src) (m :src))
        sratom (m :$sratom)
        sratom-val (when sratom (deref sratom))
        $split (m :$split)
        $split (if sratom-val sratom-val $split)
        m (-> m (dissoc :$sratom) (assoc :$split $split :src src))]
    (printchan :TID tid :EID eid)
    m))

(defn get-tab-data []
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

                                            (= k :specs) (vec v)

                                            :else v)]
                                    (vector k v))))
                          (into {}))})))
       (cons (hmi/get-adb [:main :uid :name]))
       vec))

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


(defn load-doc [doc-data extns-xref]
  (let [tids (rest (mapv :id (tab-data)))]
    (doseq [tid tids] (hmi/del-tab tid))
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
                           specs (tm :specs)
                           args (->> (merge oopts (dissoc info :fn :tid :src))
                                     seq (cons [:specs specs])
                                     (apply concat))]
                       (printchan :ARGS oopts)
                       (apply f tid label src args)
                       [tid :done])
                     (do (->> m vals first :specs hmi/update-tabs)
                         [(-> m keys first) :done]))))))))
