(ns aerial.saite.usercode
  (:require-macros
   [aerial.saite.analyzer :refer [analyzer-state]])

  (:require
   [clojure.string :as str]

   [cljs.tools.reader :refer [read-string]]
   [cljs.env :as env]
   [cljs.js :refer [empty-state eval js-eval eval-str require]]

   [aerial.saite.core]
   [aerial.saite.compiler
    :as comp
    :refer [format state load-analysis-cache!]]

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
   [aerial.hanami.templates :as ht]

   [reagent.core :as rgt]

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
    :refer [filter-choices-by-keyword single-dropdown-args-desc]]))


(cljs.js/load-analysis-cache!
 state 'aerial.saite.core
 (analyzer-state 'aerial.saite.core))

(cljs.js/load-analysis-cache!
 state 'aerial.saite.usercode
 (analyzer-state 'aerial.saite.usercode))
