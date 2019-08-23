(ns aerial.saite.savrest
  (:require

   [clojure.set :refer [map-invert]]

   [aerial.hanami.core
    :as hmi
    :refer [printchan get-adb update-adb]]

   [com.rpl.specter :as sp]
   ))


;;; Document Save and Restore ============================================= ;;;

(defn tab-data []
  (->> (sp/select [sp/ATOM :tabs :active sp/ATOM sp/ALL]
                  hmi/app-db)
       (map (fn[tab]
              (select-keys tab [:id :label :opts :specs])))))

(defn get-extn-info [tid]
  (let [m (get-adb [:tabs :extns tid])
        eid (m :eid)
        src (get-adb [:editors eid :in])
        src (if src (deref src) (m :src))]
    (printchan :TID tid :EID eid)
    (assoc m :src src)))

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
       (cons (get-adb [:main :uid :name]))
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
                           label (tm :label)
                           specs (tm :specs)
                           args (->> (dissoc info :fn :tid :src)
                                     seq (cons [:specs specs])
                                     (apply concat))]
                       (printchan :ARGS args)
                       (apply f tid label src args)
                       [tid :done])
                     (do (->> m vals first :specs hmi/update-tabs)
                         [(-> m keys first) :done]))))))))
