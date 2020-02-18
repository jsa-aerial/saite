(ns aerial.saite.core
  (:require [clojure.string :as cljstr]
            [clojure.data.csv :as csv]
            [clojure.data.json :as json]

            [cemerick.pomegranate :as pom]
            [me.raynes.fs.compression :as cmp]
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
            [aerial.hanami.data :as hd]))


(defmacro try+ [msg & body]
  `(let [name#  (get-in ~msg [:data :uid :name])
         eval?# (get-in ~msg [:data :eval])
         chkey# (get-in ~msg [:data :chankey])]
     (try
       ~@body
       (catch Error e#
         (hmi/send-msg
          name#
          {:op :error
           :data {:error (str (type e#)), :eval eval?#, :chankey chkey#
                  :msg (->  ((Throwable->map e#) :cause)
                            (cljstr/split #"\n") first
                            (cljstr/split #": ") rest
                            (->> (cljstr/join ": ")))}}))
       (catch clojure.lang.ExceptionInfo e#
         (hmi/send-msg
          name#
          {:op :error
           :data {:error (str (type e#)), :eval eval?#, :chankey chkey#
                  :msg (str (.getMessage e#) ": " (.getData e#))}}))
       (catch Exception e#
         (hmi/send-msg
          name#
          {:op :error
           :data {:error (str (type e#)), :eval eval?#, :chankey chkey#
                  :cause (str ((Throwable->map e#) :cause))
                  :msg (-> e# Throwable->map :via first :message)}})))))




(defn xform-cljform
  [clj-form]
  (hmi/print-when [:pchan :xform :cljform] :CLJ-FORM clj-form)
  (sp/transform
   sp/ALL
   (fn[v]
     (cond
       (symbol? v)
       (let [vvar (resolve v)
             vval (when vvar (var-get vvar))]
         (cond
           (and (fn? vval)
                (not (#{(var hc/xform) (var merge)} vvar)))
           (throw (Exception. (format "fns (%s) cannot be converted" (name v))))

           (= v 'RMV) v

           :else vval))

       (or (vector? v) (list? v))
       (let [hd (first v)
             hdvar (and (symbol? hd) (resolve hd))
             hdval (when hdvar (var-get hdvar))]
         (hmi/print-when [:pchan :xform :vec]
                         :V v :HDVAL hdval (= hdvar (var hc/xform)))
         (cond
           (and hdvar (fn? hdval) (= (var hc/xform) hdvar))
           (hc/xform (eval (apply xform-cljform (rest v))))

           (and hdvar (fn? hdval) (= (var merge) hdvar))
           (merge (eval (rest v)))

           (and hdvar (coll? hdval))
           (hc/xform hdval (eval (apply xform-cljform (rest v))))

           (and hdval (fn? hdval))
           (throw (Exception.
                   (format "fns (%s) cannot be converted" (name hd))))

           (and (vector? v) (coll? hd) (not (map? hd))
                (not= (first hd) 'hc/xform))
           [(apply hc/xform (xform-cljform hd))]

           :else v))

       :else v))
   clj-form))

(defn final-xform [x]
  (if (vector? x)
    (apply hc/xform x)
    (hc/xform x)))

;;; (->> (resolve (read-string "ht/point-chart")) meta :ns str)
;;; (->> (resolve (read-string "ht/point-chart")) var-get fn?)
(def dbg (atom {}))
(defmethod hmi/user-msg :read-clj [msg]
  (let [{:keys [session-name cljstg render?]} (msg :data)
        clj (try
              (let [clj (->> cljstg clojure.core/read-string)]
                (swap! dbg (fn[m] (assoc m :clj clj)))
                (->> clj xform-cljform eval final-xform))
              (catch Exception e
                {:error (format "Error %s" (or (.getMessage e) e))})
              (catch Error e
                {:error (format "Error %s" (or (.getMessage e) e))}))]
    (swap! dbg (fn[m] (assoc m :xform-clj clj)))
    (hmi/print-when [:pchan :umsg] :CLJ clj)
    (hmi/send-msg session-name :clj-read (assoc clj :render? render?))))




(defn deps [coords]
  (pom/add-dependencies
   ;;:classloader (.getParent @Compiler/LOADER)
   :coordinates coords
   :repositories (merge cemerick.pomegranate.aether/maven-central
                        {"clojars" "https://clojars.org/repo"}))
  :success)

(defmethod hmi/user-msg :set-namespace [msg]
  (let [nsinfo (msg :data)
        {:keys [nssym requires]} nsinfo
        requires-stg (if (not (seq requires))
                       ""
                       (cljstr/join "\n            " requires))]
    (try+ msg
     (binding [*ns* (find-ns 'aerial.saite.core)]
       (eval
        (read-string
         (format
          "(ns %s
             (:require [clojure.string :as str]
                       [clojure.data.csv :as csv]
                       [clojure.data.json :as json]
                       [clojure.repl :as cr]

                       [cemerick.pomegranate :as pom]
                       [com.rpl.specter :as sp]

                       [aerial.fs :as fs]
                       [aerial.utils.string :as astr]
                       [aerial.utils.io :refer [letio] :as io]
                       [aerial.utils.coll :refer [vfold] :as coll]
                       [aerial.utils.math :as m]
                       [aerial.utils.math.probs-stats :as p]
                       [aerial.utils.math.infoth :as it]

                       [aerial.hanami.common :as hc :refer [RMV]]
                       [aerial.hanami.templates :as ht]
                       [aerial.hanami.core :as hmi]
                       [aerial.hanami.data :as hd]
                       [aerial.saite.core :refer [deps]]
                       %s)))"
          nssym requires-stg)))))))


(defmethod hmi/user-msg :eval-clj [msg]
  (try+ msg
   (let [codeinfo (msg :data)
         {:keys [uid chankey nssym code]} codeinfo
         nssym (if (string? nssym) (symbol nssym) nssym)
         code (if (string? code) (read-string code) code)
         res (binding [*ns* (find-ns nssym)]
               (eval code))
         res (cond (instance? clojure.lang.Var res) (str res)
                   (instance? java.lang.Class res)  (str res)
                   (fn? res) (str res)
                   (isa? res java.lang.Class) (str res)
                   :else res)
         msg {:op :evalres :data {:chankey chankey :value res}}]
     (hmi/send-msg (uid :name) msg))))




(defmethod hmi/user-msg :save-doc [msg]
  (try+ msg
   (let [{:keys [loc info]} (msg :data)
         [_ & data] info
         {:keys [session file]} loc
         config (hmi/get-adb [:saite :cfg])
         saveloc (get-in config [:locs :save] (config :saveloc))
         dir (fs/join (fs/fullpath saveloc) session)
         filespec (fs/join dir file)]
     (hmi/printchan :Saving filespec)
     (fs/mkdirs dir)
     (binding [*print-length* nil]
       (io/with-out-writer filespec
         (prn (vec data)))))))

(defmethod hmi/user-msg :save-charts [msg]
  (try+ msg
   (let [{:keys [uid archive]} (msg :data)
         locs (hmi/get-adb [:saite :cfg :locs])
         {:keys [linux mac win]} (locs :downloads)
         os (System/getProperty "os.name")
         downloads (case os
                     "Linux" linux
                     "Mac OS X" mac
                     "Windows 10" win)
         archive (->> archive (fs/join downloads) fs/fullpath)
         uziptgt (-> :chart locs fs/fullpath)]
     #_(hmi/printchan :MSG msg :ARCHIVE archive :TGT uziptgt)

     ;; This totally sucks, but the current fileserverjs saveAs
     ;; function fires and forgets with no way of informing the caller
     ;; when it completes. So, the :save-charts msg is sent to the
     ;; server 'immediately' and so we have to wait for the download
     ;; to finish.  It probably would make the most sense to move to
     ;; the fileserverjs _streaming_ API save which _does_ have all
     ;; this information.
     (loop [sz -1
            cnt 60]
       (when (and (> cnt 0)
                  (or (not (fs/file? archive))
                      (< sz (fs/size archive))))
         (Thread/sleep 1000)
         (recur (if (fs/file? archive) (fs/size archive) -1) (dec cnt))))

     (fs/mkdirs uziptgt)
     (cmp/unzip archive uziptgt)
     (fs/rm archive))))


(defmethod hmi/user-msg :load-doc [msg]
  (try+ msg
   (let [config (hmi/get-adb [:saite :cfg])
         saveloc (get-in config [:locs :save] (config :saveloc))
         {:keys [uid location]} (msg :data)
         {:keys [session file url]} location
         dir (fs/join (fs/fullpath saveloc) session)
         file (fs/join dir file)
         data (->> (if url url file) slurp read-string)
         msg {:op :load-doc :data data}]
     (hmi/printchan :Loading (if url url file))
     (hmi/send-msg (uid :name) msg))))


#_{:op :read-data
   :data {:uid uid

          :from <:url or :file
          :path the-full-path}}
(defmethod hmi/user-msg :read-data [msg]
  (try+ msg
   (let [{:keys [uid chankey from path]} (msg :data)
         ftype (fs/ftype path)
         data (cond (= from :file) (hd/get-data (fs/fullpath path))
                    (= ftype "json") (-> path slurp json/read-str)
                    (= ftype "csv") (-> path slurp csv/read-csv)
                    :else (slurp path))
         data (if data data :BAD-DATASET)
         msg {:op :data :data {:chankey chankey :data data}}]
     (hmi/send-msg (uid :name) msg)
     (when (= data :BAD-DATASET)
       (throw
        (ex-info (format "Bad file type or empty dataset, %s" path) {}))))))



(def home-path (fs/join "~" ".saite"))

(defonce default-cfg
  {:editor
   {:name "emacs",
    :mode "clojure"
    :theme "zenburn"
    :size {:edout {:height 900
                   :width  730
                   :out-height 900
                   :out-width 1000 }
           :eddoc {:height 790
                   :width  730
                   :out-height 100
                   :out-width 730}}
    :key-bindings '{"Ctrl-F"         pe/forward-sexp
                    "Ctrl-B"         pe/backward-sexp
                    "Ctrl-Left"      pe/forward-barf-sexp
                    "Ctrl-Right"     pe/forward-slurp-sexp
                    "Ctrl-Alt-Left"  pe/backward-barf-sexp
                    "Ctrl-Alt-Right" pe/backward-slurp-sexp

                    "Ctrl-Home"      em/go-doc-start
                    "Ctrl-End"       em/go-doc-end
                    "Ctrl-L"         recenter-top-bottom
                    "Ctrl-X D"       show-doc
                    "Ctrl-X S"       show-source

                    "Ctrl-Alt-T"     insert-txt-frame
                    "Ctrl-Alt-C"     insert-cm-md
                    "Ctrl-Alt-m"     insert-md
                    "Ctrl-Alt-V"     insert-vis-frame
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

                    "Ctrl-X Ctrl-E"  evalxe
                    "Ctrl-X Ctrl-C"  eval-mixed-cc ;evalcc
                    "Ctrl-X J"       evaljvm-xe
                    "Ctrl-X Ctrl-J"  evaljvm-cc
                    "Ctrl-X Ctrl-M"  eval-mixed-cc}}

   :interactive-tab
   {:edtype :interactive-doc :nssym "doc.code"
    :order :row, :eltsper 1, :rgap 20, :cgap 20 :size "auto"
    :layout :up-down, :ed-out-order :first-last
    :md-defaults {:md {:vmargin "50px"
                       :margin  "200px"
                       :width "800px"
                       :font-size "16px"}
                  :cm {:width "500px" ;;:height "30px"
                       :out-width "500px" ;; :out-height "0px"
                       :ed-out-order :first-last}}
    :doc {:max-height "850px"
          :max-width "2000px"}}

   :locs
   {:save  (fs/join home-path "Docs")
    :chart (fs/join home-path "Charts")
    :data  (fs/join home-path "Data")
    :downloads {:linux (fs/join home-path "Downloads")
                :mac (fs/join home-path "Downloads")
                :win (fs/join home-path "Downloads")}}
   })


(defn init [port]
  (let [cfgfile (-> home-path fs/fullpath (fs/join "config.edn"))
        cfg (if (fs/exists? cfgfile)
              (-> cfgfile slurp read-string)
              default-cfg)]
    (hmi/update-adb [:saite :cfg] (assoc cfg :port port)))

  (hc/add-defaults
   :HEIGHT 400 :WIDTH 450
   :USERDATA {:tab {:id :TID, :label :TLBL, :opts :TOPTS}
              :frame {:top :TOP, :bottom :BOTTOM,
                      :left :LEFT, :right :RIGHT
                      :fid :FID :at :AT :pos :POS}
              :opts :OPTS
              :vid :VID,
              :msgop :MSGOP,
              :session-name :SESSION-NAME}
   :AT :end :POS :after
   :MSGOP :tabs, :SESSION-NAME "Exploring"
   :TID :expl1, :TLBL #(-> :TID % name cljstr/capitalize)
   :OPTS (hc/default-opts :vgl)
   :TOPTS {:order :row, :eltsper 2 :size "auto"}))


(defn config-info [data-map]
  (let [config (hmi/get-adb [:saite :cfg])
        saveloc (get-in config [:locs :save] (config :saveloc))
        sessions (-> saveloc fs/fullpath (fs/directory-files ""))
        port (config :port)
        quick-doc (slurp (format "http://localhost:%s/doc/quick.md" port))]
    (assoc
     data-map
     :save-info (mapv #(vector (fs/basename %)
                               (->> (fs/directory-files % "")
                                    sort
                                    (mapv (fn[f] (-> f fs/basename
                                                    (fs/replace-type ""))))))
                      sessions)
     :editor (config :editor)
     :interactive-tab (config :interactive-tab)
     :locs (config :locs)
     :doc {:quick quick-doc}) ))


(defn start [port]
  (init port)
  (hmi/start-server port
                    :connfn config-info
                    :idfn (constantly "Exploring")
                    :title "咲いて"
                    :logo "images/small-in-bloom.png"
                    :img "images/in-bloom.png"))

(defn stop []
  (hmi/stop-server))
