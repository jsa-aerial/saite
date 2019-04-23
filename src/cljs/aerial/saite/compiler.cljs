(ns aerial.saite.compiler
  (:require-macros
   [aerial.saite.analyzer :refer [analyzer-state]])

  (:require
   [clojure.string :as cljstr]

   [cljs.tools.reader :refer [read-string]]
   [cljs.env :as env]
   [cljs.js :refer [empty-state eval js-eval eval-str require]]

   [aerial.hanami.core
    :as hmi
    :refer [printchan user-msg
            re-com-xref xform-recom
            default-header-fn start
            update-adb get-adb
            get-vspec update-vspecs
            get-tab-field add-tab update-tab-field active-tabs
            vgl app-stop]]
   [aerial.hanami.common
    :as hc
    :refer [RMV]]
   [aerial.hanami.templates :as ht]

   ))



(def state (cljs.js/empty-state))

(defn evaluate [source cb]
  (let [source (cljstr/replace source #"hmi/" "aerial.hanami.core/")
        source (cljstr/replace source #"hcm/" "aerial.hanami.common/")
        source (cljstr/replace source #"htm/" "aerial.hanami.templates/")]
    (cljs.js/eval-str state source nil
                      {:eval cljs.js/js-eval
                       :ns 'aerial.saite.compiler
                       :context :expr}
                      cb)))


#_(ambient.main.core/analyzer-state 'aerial.hanami.core)
(defn load-hanami-analysis-cache! []
  (cljs.js/load-analysis-cache!
   state 'aerial.saite.compiler
   (analyzer-state 'aerial.saite.compiler))
  (cljs.js/load-analysis-cache!
   state 'aerial.hanami.core
   (analyzer-state 'aerial.hanami.core))
  (cljs.js/load-analysis-cache!
   state 'aerial.hanami.templates
   (analyzer-state 'aerial.hanami.templates))
  (cljs.js/load-analysis-cache!
   state 'aerial.hanami.common
   (analyzer-state 'aerial.hanami.common))
  :done)


(def expr* (atom nil))
(defn expr*! [x] (reset! expr* x))

(load-hanami-analysis-cache!)
