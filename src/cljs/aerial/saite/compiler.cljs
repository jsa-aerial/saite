(ns aerial.saite.compiler
  (:require-macros
   [aerial.saite.analyzer :refer [analyzer-state]])

  (:require
   [clojure.string :as cljstr]

   [goog.string :as gstring]
   [goog.string.format]

   [cljs.tools.reader :refer [read-string]]
   [cljs.env :as env]
   [cljs.js :refer [empty-state eval js-eval eval-str require]]

   [aerial.saite.savrest
    :refer [update-ddb get-ddb]]

   [aerial.hanami.core
    :as hmi
    :refer [printchan user-msg
            re-com-xref xform-recom
            default-header-fn start
            get-vspec update-vspecs
            get-tab-field add-tab update-tab-field
            add-to-tab-body remove-from-tab-body
            active-tabs
            md vgl app-stop]]
   [aerial.hanami.common
    :as hc
    :refer [RMV]]
   [aerial.hanami.templates :as ht]

   [re-com.core
    :as rcm
    :refer [h-box v-box box gap line h-split v-split scroller
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




(defn format [s & params]
  (apply gstring/format s params))

(def state (cljs.js/empty-state))



#_(defn get-source-n-cache [{:keys [name macros path]}]
  "Experimental - was for Andare core.async self hosted use.  But
  Andare does not work in the browser.  So, this is no longer used"
  (let [ch (as/chan)
        chankey (keyword (gensym "chan-"))
        res (volatile! nil)]
    (update-ddb [:main :chans chankey] ch)
    (hmi/send-msg {:op :cljs-require
                   :data {:uid (hmi/get-adb [:main :uid])
                          :chankey chankey
                          :path path}})
    ch))

(defn loader-fn [info-map cb]
  (printchan info-map)
  #_(if (and (info-map :macros)
           (re-find #"asyncxxx$" (info-map :path)))
    (let [res (as/<! (get-source-n-cache info-map))
          src (res :value)]
      (printchan src)
      (cb {:lang :clj :source src})))
  (cb  {:lang :js :source ""}))


(defn evaluate
  ([nssym source cb]
   (if (string? source)
     (let [source (cljstr/replace source #"hmi/" "aerial.hanami.core/")
           source (cljstr/replace source #"hcm/" "aerial.hanami.common/")
           source (cljstr/replace source #"htm/" "aerial.hanami.templates/")]
       (cljs.js/eval-str state source nil
                         {:eval cljs.js/js-eval
                          :ns nssym
                          :load loader-fn
                          :context :expr}
                         cb))
     (try
       (cljs.js/eval state source
                     {:eval cljs.js/js-eval
                      :ns nssym
                      :context :expr}
                     cb)
       (catch :default cause
         (cb {:error (prn-str cause)})))))
  ([source cb]
   (evaluate 'aerial.saite.compiler source cb)))


(def base-requires
  "[clojure.string :as str]

   [aerial.hanami.core :as hmi :refer [md]]
   [aerial.hanami.common :as hc :refer [RMV]]
   [aerial.hanami.templates :as ht]
   [aerial.hanasu.common :refer [update-db get-db]]
   [aerial.saite.compiler :refer [format]]
   [aerial.saite.core :as sc :refer [read-data]]

   [reagent.core :as rgt]
   [re-com.core
    :refer [h-box v-box box gap line h-split v-split scroller
            button row-button md-icon-button md-circle-icon-button info-button
            input-text input-password input-textarea
            label title p
            single-dropdown
            checkbox radio-button slider progress-bar throbber
            horizontal-bar-tabs vertical-bar-tabs
            modal-panel popover-content-wrapper popover-anchor-wrapper]]
  ")

(defn add-requires [base requires]
  (reduce (fn[R r]
            (format (str R "\n         %s") r))
          base-requires requires))

(defn set-namespace
  [nssym & {:keys [base requires cljrequires]
            :or {base base-requires cljrequires []}}]
  (evaluate
   (format "(ns %s\n  (:require\n   %s))"
    (name nssym)
    (add-requires base requires))
   println)

  (hmi/send-msg
   {:op :set-namespace
    :data {:nssym nssym :requires cljrequires}}))




#_(ambient.main.core/analyzer-state 'aerial.hanami.core)
(defn load-analysis-cache! []
  (cljs.js/load-analysis-cache!
   state 'aerial.saite.compiler
   (analyzer-state 'aerial.saite.compiler))

  (cljs.js/load-analysis-cache!
   state 're-com.core
   (analyzer-state 're-com.core))

  (cljs.js/load-analysis-cache!
   state 'aerial.saite.core
   (analyzer-state 'aerial.saite.core))

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

(load-analysis-cache!)
