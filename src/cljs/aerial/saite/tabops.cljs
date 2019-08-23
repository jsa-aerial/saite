(ns aerial.saite.tabops
  (:require
   [cljs.core.async
    :as async
    :refer (<! >! put! chan)
    :refer-macros [go go-loop]]
   [clojure.string :as cljstr]

   [aerial.hanami.core
    :as hmi
    :refer [printchan user-msg
            re-com-xref xform-recom
            default-header-fn start
            update-adb get-adb
            get-vspec update-vspecs
            get-tab-field add-tab update-tab-field
            add-to-tab-body remove-from-tab-body
            active-tabs
            md vgl app-stop]]
   [aerial.hanami.common
    :as hc
    :refer [RMV]]

   [com.rpl.specter :as sp]

   [reagent.core :as rgt]

   ))




(defn insert-val [x v &{:keys [at pos] :or {pos :before}}]
  (let [index (sp/select-one [sp/INDEXED-VALS #(-> % second (= at)) sp/FIRST] v)
        index (if (= pos :before) index (inc index))]
    (sp/setval (sp/before-index index) x v)))

;;;com.rpl.specter.zipper
;;;
;;; (setval [z/VECTOR-ZIP (z/find-first #(= "b" %)) z/INNER-LEFT]
;;;         [77] ["a" "b" "c"])
;;; => ["a" 77 "b" "c"]




(def undo-redo-stacks (atom {:undo [] :redo []}))

(defn get-undo [] (@undo-redo-stacks :undo))
(defn get-redo [] (@undo-redo-stacks :redo))

(defn push-undo [x]
  (swap! undo-redo-stacks (fn[m] (assoc m :undo (conj (m :undo) x)))))

(defn pop-undo []
  (let [x (peek (@undo-redo-stacks :undo))]
    (when x
      (swap! undo-redo-stacks (fn[m] (assoc m :undo (pop (m :undo))))))
    x))

(defn push-redo [x]
  (swap! undo-redo-stacks (fn[m] (assoc m :redo (conj (m :redo) x)))))

(defn pop-redo []
  (let [x (peek (@undo-redo-stacks :redo))]
    (when x
      (swap! undo-redo-stacks (fn[m] (assoc m :redo (pop (m :redo))))))
    x))

(defn undo []
  (let [x (pop-undo)]
    (if x
      (let [cur (sp/select-one [sp/ATOM :tabs :active sp/ATOM] hmi/app-db)]
        (sp/setval [sp/ATOM :tabs :active sp/ATOM] x hmi/app-db)
        (hmi/set-cur-tab (-> x last :id))
        (push-redo cur))
      (js/alert "Nothing more to undo"))))

(defn redo []
  (let [x (pop-redo)]
    (if x
      (let [cur (sp/select-one [sp/ATOM :tabs :active sp/ATOM] hmi/app-db)]
        (sp/setval [sp/ATOM :tabs :active sp/ATOM] x hmi/app-db)
        (hmi/set-cur-tab (-> x last :id))
        (push-undo cur))
      (js/alert "Nothing more to redo"))))


(defn get-tab-frames []
  (->> (hmi/get-cur-tab :specs)
       (mapv #(-> % :usermeta :frame :fid))
       (filter identity)
       (mapv #(do {:id % :label (-> % name cljstr/capitalize)}))))

(defn remove-frame [fid]
  (let [tid (hmi/get-cur-tab :id)
        x (sp/select-one [sp/ATOM :tabs :active sp/ATOM] hmi/app-db)]
    (push-undo x)
    (hmi/remove-from-tab-body tid fid)))


(defn add-frame [picframe at pos]
  (let [tid (hmi/get-cur-tab :id)
        x (sp/select-one [sp/ATOM :tabs :active sp/ATOM] hmi/app-db)]
    (push-undo x)
    (hmi/add-to-tab-body
     tid picframe
     :at at :position pos)))


(defn update-frame [element specinfo]
  (let [tid (hmi/get-cur-tab :id)
        fid (get-in specinfo [:usermeta :frame :fid])
        vid (get-in specinfo [:usermeta :vid])
        x (sp/select-one [sp/ATOM :tabs :active sp/ATOM] hmi/app-db)]
    (push-undo x)
    (if (= element :vis)
      (let [elt (js/document.getElementById (name vid))]
        (if elt
          (hmi/visualize specinfo elt)
          (add-frame specinfo 1 2)))
      (hmi/update-frame
       tid fid element specinfo))))


