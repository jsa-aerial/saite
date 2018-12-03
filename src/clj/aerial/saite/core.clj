(ns aerial.saite.core
  (:require [clojure.string :as cljstr]
            [clojure.data.csv :as csv]
            [clojure.data.json :as json]

            [com.rpl.specter :as sp]

            [aerial.fs :as fs]
            [aerial.utils.string :as str]
            [aerial.utils.io :refer [letio] :as io]
            [aerial.utils.coll :refer [vfold] :as coll]
            [aerial.utils.math :as m]
            [aerial.utils.math.probs-stats :as p]
            [aerial.utils.math.infoth :as it]

            [aerial.hanami.common :as hc :refer [RMV]]
            [aerial.hanami.templates :as ht]
            [aerial.hanami.core :as hmi]

            [aerial.saite.common :as ac]
            [aerial.saite.templates :as at]))


;;; (->> (resolve (read-string "ht/point-chart")) meta :ns str)
;;; (->> (resolve (read-string "ht/point-chart")) var-get fn?)
(defmethod hmi/user-msg :read-clj [msg]
  (let [{:keys [session-name cljstg render?]} (msg :data)
        uuids (hmi/get-adb session-name)
        clj (try
              (let [clj (->> cljstg clojure.core/read-string)
                    [spec params] (when (vector? clj)[(first clj) (rest clj)])]
                (cond
                  (and spec (symbol? spec))
                  (let [v (resolve spec)
                        v (when v (var-get v))]
                    (if (nil? v)
                      (format "Error: unknown var '%s'" (name spec))
                      (if (not (fn? v))
                        (apply hc/xform v params)
                        (format "Error: fns (%s) cannot be converted"
                                (name spec)))))

                  spec (apply hc/xform spec params)

                  :else (hc/xform clj)))
              (catch Exception e
                (format "Error %s" (or (.getMessage e) e)))
              (catch Error e
                (format "Error %s" (or (.getMessage e) e))))]
    (hmi/printchan :CLJ clj)
    (hmi/s! uuids :clj-read (assoc clj :render? render?))))


(defn init []
  (hc/add-defaults
   :HEIGHT 400 :WIDTH 450
   :USERDATA {:tab {:id :TID, :label :TLBL, :opts :TOPTS}
              :opts :OPTS
              :vid :VID, :msgop :MSGOP, :session-name :SESSION-NAME}
   :VID RMV, :MSGOP :tabs, :SESSION-NAME "Exploring"
   :TID :expl1, :TLBL #(-> :TID % name cljstr/capitalize)
   :OPTS (hc/default-opts :vgl)
   :TOPTS {:order :row, :eltsper 2 :size "auto"}))



(defn start [port]
  (hmi/start-server port
                    :idfn (constantly "Exploring")
                    :title "咲いて"
                    :logo "images/small-in-bloom.png"
                    :img "images/in-bloom.png")
  (init))

(defn stop []
  (hmi/stop-server))
