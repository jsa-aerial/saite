(ns aerial.saite.tabs
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
            get-vspec update-vspecs
            get-tab-field add-tab update-tab-field
            add-to-tab-body remove-from-tab-body
            active-tabs
            md vgl app-stop]]
   [aerial.hanami.common
    :as hc
    :refer [RMV]]

   [aerial.saite.splits :as ass]
   [aerial.saite.savrest
    :refer [update-ddb get-ddb]]
   [aerial.saite.codemirror
    :as cm
    :refer [code-mirror cm]]
   [aerial.saite.compiler :refer [set-namespace]]
   [aerial.saite.tabops
    :as tops
    :refer [push-undo undo redo get-tab-frames remove-frame]]

   [com.rpl.specter :as sp]

   [reagent.core :as rgt]

   [re-com.core
    :as rcm
    :refer [h-box v-box box border gap line h-split v-split scroller
            button row-button md-icon-button md-circle-icon-button info-button
            input-text input-password input-textarea
            label title p
            single-dropdown selection-list
            checkbox radio-button slider progress-bar throbber
            horizontal-bar-tabs vertical-bar-tabs
            modal-panel popover-content-wrapper popover-anchor-wrapper]
    :refer-macros [handler-fn]]
   [re-com.box
    :refer [flex-child-style]]
   [re-com.dropdown
    :refer [filter-choices-by-keyword single-dropdown-args-desc]]

   ))




;;; Interactive Editor Tab Constructors =================================== ;;;

#_(evaluate "(ns mycode.test
 (:require [clojure.string :as str]
           [aerial.hanami.core :as hmi]
           [aerial.hanami.common :as hc]
           [aerial.hanami.templates :as ht]
           [aerial.saite.core :as asc]
           [com.rpl.specter :as sp]))" println)


(defn ^:export editor-repl-tab
  [tid label src & {:keys [width height out-height layout ns]
                    :or {width "730px"
                         height "700px"
                         out-height "700px"
                         layout :left-right
                         ns 'aerial.saite.usercode}}]
  (let [cmfn (cm)
        eid (str "ed-" (name tid))
        uinfo {:fn ''editor-repl-tab
               :tid tid
               :eid eid
               :width width
               :height height
               :out-height out-height
               :layout layout
               :ns ns
               :src src}]
    (set-namespace ns)
    (update-ddb [:tabs :extns tid] uinfo)
    (add-tab
     {:id tid
      :label label
      :specs []
      :opts {:order :row, :eltsper 1, :size "auto"
             :wrapfn (fn[_]
                       [box
                        :child [cmfn :id eid
                                :width width
                                :height height
                                :out-height out-height
                                :layout layout
                                :src src]
                        :width "2048px"])}})))


(defn ^:export interactive-doc-tab
  [tid label src & {:keys [width height out-height $split
                           ns specs order eltsper rgap cgap size]
                    :or {width "730px"
                         height "700px"
                         out-height "100px"
                         $split (get-ddb [:tabs :extns :$split])
                         ns 'aerial.saite.usercode
                         specs []
                         order :row eltsper 1 :rgap "20px" :cgap "20px"
                         size "auto"}}]
  (let [cmfn (cm)
        eid (str "ed-" (name tid))
        sratom (rgt/atom $split)
        uinfo {:fn ''interactive-doc-tab
               :tid tid
               :eid eid
               :width width
               :height height
               :out-height out-height
               :$split $split
               :$sratom sratom
               :ns ns
               :src src}
        hsfn (ass/h-split
              :panel-1 "fake p1"
              :panel-2 "fake p2"
              :split-perc sratom
              :on-split-change
              #(update-ddb
                [:tabs :extns (hmi/get-cur-tab :id) :$split]
                (let [sp (/ (.round js/Math (* 100 %)) 100)]
                  (if (<= sp 3.0) 0.0 sp)))
              :width "2048px")]
    (set-namespace ns)
    (update-ddb [:tabs :extns tid] uinfo)
    (add-tab
     {:id tid
      :label label
      :specs specs
      :opts {:order order, :eltsper eltsper,
             :rgap rgap :cgap cgap :size size
             :wrapfn (fn[hcomp]
                       [hsfn
                        :panel-1 [cmfn :id eid
                                  :width width
                                  :height height
                                  :out-height out-height
                                  :src src]
                        :panel-2 [scroller
                                  :max-height "800px"
                                  :max-width "1800px"
                                  :align :start
                                  :child hcomp]])}})
    (let [opts (hmi/get-tab-field tid :opts)
          s-f-pairs (hmi/make-spec-frame-pairs tid opts specs)]
      (hmi/update-tab-field tid :compvis (hmi/vis-list tid s-f-pairs opts)))))


(def extns-xref
  (into {} (map vector
                '[editor-repl-tab interactive-doc-tab]
                [editor-repl-tab interactive-doc-tab])))




;;; General - multiple uses =============================================== ;;;

(defn alert-panel [txt closefn]
  (printchan :alert-panel)
  [modal-panel
   :child [re-com.core/alert-box
           :id 1 :alert-type :danger
           :heading txt
           :closeable? true
           :on-close closefn]
   :backdrop-color "grey" :backdrop-opacity 0.0])


(defn input-area [label-txt model]
  [h-box :gap "10px"
   :children [[input-text
               :model model
               :width "60px" :height "20px"
               :on-change #(reset! model %)]
              [label :label label-txt]]])


(defn ok-cancel [donefn cancelfn]
  [h-box :gap "5px" :justify :end
   :children
   [[md-circle-icon-button
     :md-icon-name "zmdi-check-circle"
     :tooltip "OK"
     :on-click donefn]
    [md-circle-icon-button
     :md-icon-name "zmdi-close"
     :tooltip "Cancel"
     :on-click cancelfn]]])




;;; Header Editor Mgt Components ========================================== ;;;

(defn editor-box []
  [border :padding "2px" :radius "2px"
   :l-border "1px solid lightgrey"
   :r-border "1px solid lightgrey"
   :b-border "1px solid lightgrey"
   :child [h-box
           :gap "10px"
           :children
           [[md-circle-icon-button
             :md-icon-name "zmdi-unfold-more" :size :smaller
             :tooltip "Open Editor Panel"
             :on-click
             #(let [tid (hmi/get-cur-tab :id)
                    sratom (get-ddb [:tabs :extns tid :$sratom])
                    last-split (get-ddb [:tabs :extns tid :$split])
                    last-split (if (= 0 last-split)
                                 (get-ddb [:tabs :extns :$split])
                                 last-split)]
                (reset! sratom last-split))]

            [md-circle-icon-button
             :md-icon-name "zmdi-unfold-less" :size :smaller
             :tooltip "Collapse Editor Panel "
             :on-click
             #(let [tid (hmi/get-cur-tab :id)
                    sratom (get-ddb [:tabs :extns tid :$sratom])]
                (reset! sratom 0))]]]])




;;; Header Help Component ================================================= ;;;

(defn help-modal [show?]
  (let [closefn (fn[event] (reset! show? false))]
    (fn[show?]
      [modal-panel
       :backdrop-color   "grey"
       :backdrop-opacity 0.4
       :child [v-box :gap "10px"
               :children
               [[label :style {:font-size "18px"} :label "Synopsis:"]
                [scroller
                 :max-height "400px"
                 :max-width "600px"
                 :child [md {:style {:fond-size "16px" :width "600px"}}
                         (get-ddb [:main :doc :quick])]]
                [h-box :gap "5px" :justify :end
                 :children
                 [[md-circle-icon-button
                   :md-icon-name "zmdi-close"
                   :tooltip "Close"
                   :on-click closefn]]]]]])))

(defn help-box []
  (let [quick? (rgt/atom false)]
    (fn []
      [h-box :gap "5px" :justify :end
       :children
       [[md-circle-icon-button
         :md-icon-name "zmdi-help" :size :smaller
         :tooltip "Quick Help"
         :on-click #(reset! quick? true)]
        [md-circle-icon-button
         :md-icon-name "zmdi-info" :size :smaller
         :tooltip "Doc Help"
         :on-click #()]
        (when @quick? [help-modal quick?])
        [gap :size "10px"]]])))




;;; Header Tab Mgt Components ============================================= ;;;

(defn del-tab [tid]
  (let [x (sp/select-one [sp/ATOM :tabs :active sp/ATOM] hmi/app-db)
        eid (get-ddb [:tabs :extns tid :eid])]
    (push-undo x)
    (update-ddb [:tabs :extns tid] :rm
                [:editors eid] :rm)
    (hmi/del-tab tid)))

(defn add-interactive-tab [info-map]
  (let [x (sp/select-one [sp/ATOM :tabs :active sp/ATOM] hmi/app-db)
        {:keys [edtype ns id label
                order eltsper
                width height out-height
                size rgap cgap layout]} info-map]
    (push-undo x)
    (cond
      (= :converter edtype) (printchan :NYI)

      (= :editor edtype)
      (editor-repl-tab
       id label "" :ns ns
       :width width :height height
       :out-height out-height :layout layout)

      :else
      (interactive-doc-tab
       id label "" :ns ns
       :width width :height height
       :out-height out-height
       :order order :eltsper eltsper
       :rgap rgap :cgap cgap :size size))

    (printchan info-map)))


(defn duplicate-cur-tab [info]
  (let [{:keys [tid label nssym]} info
        ctid (hmi/get-cur-tab :id)
        uinfo (or (get-ddb [:tabs :extns ctid]) {:fn [:_ :NA]})
        eid (str "ed-" (name tid))
        {:keys [width height out-height layout]} uinfo
        src (deref (get-ddb [:editors (uinfo :eid) :in]))
        tabval (hmi/get-tab-field ctid)
        {:keys [specs opts]} tabval
        {:keys [order eltsper size]} opts
        edtype (second (uinfo :fn))]
    (case edtype
      :NA
      (add-tab
       {:id tid :label label :specs specs :opts opts})

      editor-repl-tab
      (editor-repl-tab
       tid label src :ns nssym
       :width width :height height
       :out-height out-height :layout layout)

      interactive-doc-tab
      (interactive-doc-tab
       tid label src :ns nssym
       :width width :height height :out-height out-height
       :specs specs :order order :eltsper eltsper :size size))))


(defn edit-cur-tab [info]
  (let [curtab (hmi/get-cur-tab)
        tid (curtab :id)
        label (curtab :label)
        opts (curtab :opts)
        specs (curtab :specs)
        {:keys [label nssym order eltsper rgap cgap size]} info
        newopts (merge opts (dissoc info :label :nssym))
        s-f-pairs (hmi/make-spec-frame-pairs tid newopts specs)]
    (update-ddb [:tabs :extns tid :ns] nssym)
    (hmi/update-tab-field tid :opts newopts)
    (hmi/update-tab-field tid :label label)
    (hmi/update-tab-field tid :compvis (hmi/vis-list tid s-f-pairs newopts))))



(defn file-new [session-name file-name donefn cancelfn]
  [v-box
   :gap "10px"
   :children [[label
               :style {:font-size "18px"}
               :label "Session"]
              [input-text
               :model session-name
               :width "300px" :height "26px"
               :on-change #(reset! session-name %)]
              [label
               :style {:font-size "18px"}
               :label "File"]
              [input-text
               :model file-name
               :width "300px" :height "26px"
               :on-change #(reset! file-name %)]
              [ok-cancel donefn cancelfn]]])

(defn urldoc [url donefn cancelfn]
  [v-box
   :gap "10px"
   :children [[label
               :style {:font-size "18px"}
               :label "URL"]
              [input-text
               :model url
               :width "700px" :height "26px"
               :on-change #(reset! url %)]
              [ok-cancel donefn cancelfn]]])

(defn file-modal [choices session-name file-name mode url donefn cancelfn]
  (let [sessions (->> choices deref keys sort (mapv (fn[k] {:id k :label k})))
        doc-files  (rgt/atom (->> session-name deref (#(@choices %))
                                  (mapv (fn[k] {:id k :label k}))))
        url? (rgt/atom false)
        urldonefn (fn[event] (reset! url? false) (donefn event))

        new? (rgt/atom false)
        newdonefn (fn[event]
                    (let [fname @file-name
                          names (->> doc-files deref (map :id) (cons fname))
                          newdfs (->> names sort
                                      (mapv (fn[k] {:id k :label k})))]
                      (reset! doc-files newdfs)
                      (reset! choices (assoc @choices
                                             @session-name (vec names)))
                      (printchan @doc-files)
                      (reset! new? false)))]
    (fn [choices session-name file-name mode url donefn cancelfn]
      [modal-panel
       :backdrop-color   "grey"
       :backdrop-opacity 0.4
       :child [v-box
               :gap "10px"
               :children
               [(when (= (deref mode) :save)
                  [checkbox
                   :model new?
                   :label "New location"
                   :on-change #(reset! new? %)])
                (when (not= (deref mode) :save)
                  [checkbox
                   :model url?
                   :label "URL"
                   :on-change #(reset! url? %)])
                (cond
                  @new? [file-new session-name file-name newdonefn cancelfn]

                  @url? [urldoc url urldonefn cancelfn]

                  :else
                  [v-box
                   :gap "10px"
                   :children
                   [[label
                     :style {:font-size "18px"}
                     :label "Session"]
                    [single-dropdown
                     :choices sessions
                     :model session-name
                     :width "300px"
                     :on-change (fn[nm]
                                  (printchan :SESSION nm)
                                  (reset! session-name nm)
                                  (reset! doc-files
                                          (->> (#(@choices nm))
                                               (mapv (fn[k]
                                                       {:id k :label k}))))
                                  (reset! file-name (-> doc-files deref
                                                        first :id)))]
                    [gap :size "10px"]
                    [label
                     :style {:font-size "18px"}
                     :label "File"]
                    [single-dropdown
                     :choices doc-files
                     :model file-name
                     :width "300px"
                     :on-change #(do (printchan :FILE %)
                                     (reset! file-name %))]

                    [ok-cancel donefn cancelfn]]])]]])))




(defn px [x] (str x "px"))


(defn next-tid-label [edtype]
  (let [i (inc (count (get-ddb [:tabs :extns])))
        [tx lx] (if (= edtype :editor)
                  ["ed" "Editor "]
                  ["chap" "Chapter "])
        tid (str tx i)
        label (str lx i)]
    [tid label]))

(defn add-modal [show?]
  (let [edtype (rgt/atom :interactive-doc)
        order (rgt/atom :row)
        eltsper (rgt/atom "1")
        rgap (rgt/atom "20")
        cgap (rgt/atom "20")
        [tx lx] (next-tid-label @edtype)
        tid (rgt/atom tx)
        tlabel (rgt/atom lx)
        nssym (rgt/atom "doc.code")
        advance? (rgt/atom false)
        width (rgt/atom "730")
        height (rgt/atom "700")
        out-height (rgt/atom (if (= @edtype :interactive-doc) "100" "700"))
        size (rgt/atom "auto")
        layout (rgt/atom :up-down)
        donefn (fn[]
                 (go (async/>! (hmi/get-adb [:main :chans :com])
                               {:edtype @edtype :ns (symbol @nssym)
                                :id (keyword @tid) :label @tlabel
                                :order @order :eltsper (js/parseInt @eltsper)
                                :rgap (px @rgap) :cgap (px @cgap)
                                :width (px @width) :height (px @height)
                                :out-height (px @out-height) :size @size
                                :layout @layout}))
                 (reset! show? false) nil)
        cancelfn (fn[]
                   (go (async/>! (hmi/get-adb [:main :chans :com]) :cancel))
                   (reset! show? false) nil)]
    (fn [show?]
      [modal-panel
       :backdrop-color   "grey"
       :backdrop-opacity 0.4
       :child [v-box
               :gap "10px"
               :children
               [[h-box :gap "10px"
                 :children
                 [[v-box :gap "10px"
                   :children
                   [[label :style {:font-size "18px"} :label "Type to add"]
                    [radio-button
                     :label "Interactive Doc"
                     :value :interactive-doc
                     :model edtype
                     :label-style (when (= :interactive-doc @edtype)
                                    {:font-weight "bold"})
                     :on-change #(let [[tx lx] (next-tid-label :doc)]
                                   (reset! layout :up-down)
                                   (reset! out-height "100")
                                   (reset! tid tx)
                                   (reset! tlabel lx)
                                   (reset! edtype %))]
                    [radio-button
                     :label "Editor and Output"
                     :value :editor
                     :model edtype
                     :label-style (when (= :editor @edtype)
                                    {:font-weight "bold"})
                     :on-change #(let [[tx lx] (next-tid-label :editor)]
                                   (reset! layout :left-right)
                                   (reset! out-height "700")
                                   (reset! tid tx)
                                   (reset! tlabel lx)
                                   (reset! edtype %))]
                    [radio-button
                     :label "<-> Converter"
                     :value :converter
                     :model edtype
                     :label-style (when (= :converter @edtype)
                                    {:font-weight "bold"})
                     :on-change #(reset! edtype %)]]]
                  [v-box :gap "10px"
                   :children
                   [[label :style {:font-size "18px"} :label "Ordering"]
                    [radio-button
                     :label "Row Ordered"
                     :value :row
                     :model order
                     :label-style (when (= :row @order) {:font-weight "bold"})
                     :on-change #(do (reset! size "auto")
                                     (reset! order %))]
                    [radio-button
                     :label "Column Ordered"
                     :value :col
                     :model order
                     :label-style (when (= :col @order) {:font-weight "bold"})
                     :on-change #(do (reset! size "none")
                                     (reset! order %))]
                    [h-box :gap "10px"
                     :children [[input-text
                                 :model eltsper
                                 :width "40px" :height "20px"
                                 :on-change #(reset! eltsper %)]
                                [label :label (str "Elts/" (if (= @order :row)
                                                             "row" "col"))]]]]]
                  [v-box :gap "10px"
                   :children
                   [[checkbox
                     :model advance?
                     :label "Advanced Options"
                     :on-change #(reset! advance? %)]
                    (when @advance?
                      [h-box :gap "20px"
                       :children
                       [[v-box :gap "10px"
                         :children
                         [[input-area "Editor Width" width]
                          [input-area "Editor Height" height]
                          [input-area "Output Height" out-height]]]
                        [v-box :gap "10px"
                         :children
                         [[input-area "Row Gap" rgap]
                          [input-area "Col Gap" cgap]
                          [input-area "Flex size" size]]]
                        [v-box :gap "10px"
                         :children
                         [[label :label "Editor / Output Layout"]
                          [radio-button
                           :label "Left-Right"
                           :value :left-right
                           :model layout
                           :label-style (when (= :left-right @layout)
                                          {:font-weight "bold"})
                           :on-change #(reset! layout %)]
                          [radio-button
                           :label "Up-Down"
                           :value :up-down
                           :model layout
                           :label-style (when (= :up-down @layout)
                                          {:font-weight "bold"})
                           :on-change #(reset! layout %)]]]]])]]
                  ]]
                [h-box :gap "10px"
                 :children [[label
                             :style {:font-size "18px"}
                             :label "Id"]
                            [input-text
                             :model tid
                             :width "200px" :height "26px"
                             :on-change
                             #(do (reset! tid %)
                                  (reset! tlabel (cljstr/capitalize %)))]
                            [gap :size "10px"]
                            [label
                             :style {:font-size "18px"}
                             :label "Label"]
                            [input-text
                             :model tlabel
                             :width "200px" :height "26px"
                             :on-change #(reset! tlabel %)]]]
                [h-box :gap "10px"
                 :children [[label
                             :style {:font-size "18px"}
                             :label "Namespace"]
                            [input-text
                             :model nssym
                             :width "200px" :height "26px"
                             :on-change
                             #(reset! nssym %)]]]
                [ok-cancel donefn cancelfn] ]]])))


(defn dup-modal [show?]
  (let [curtab (hmi/get-cur-tab)
        tid (rgt/atom (str (-> curtab :id name) "2"))
        tlabel (rgt/atom (str (curtab :label) " 2"))
        nssym (rgt/atom (name (get-ddb [:tabs :extns (curtab :id) :ns])))
        donefn (fn[]
                 (go (async/>! (hmi/get-adb [:main :chans :com])
                               {:tid (keyword @tid) :label @tlabel
                                :nssym (symbol @nssym)}))
                 (reset! show? false))
        cancelfn (fn[]
                   (go (async/>! (hmi/get-adb [:main :chans :com]) :cancel))
                   (reset! show? false))]
    (fn [show?]
      [modal-panel
       :backdrop-color   "grey"
       :backdrop-opacity 0.4
       :child [v-box
               :gap "10px"
               :children
               [[h-box :gap "10px"
                 :children [[label
                             :style {:font-size "18px"}
                             :label "Id"]
                            [input-text
                             :model tid
                             :width "200px" :height "26px"
                             :on-change
                             #(do (reset! tid %)
                                  (reset! tlabel (cljstr/capitalize %)))]
                            [gap :size "10px"]
                            [label
                             :style {:font-size "18px"}
                             :label "Label"]
                            [input-text
                             :model tlabel
                             :width "200px" :height "26px"
                             :on-change #(reset! tlabel %)]]]
                [h-box :gap "10px"
                 :children [[label
                             :style {:font-size "18px"}
                             :label "Namespace"]
                            [input-text
                             :model nssym
                             :width "200px" :height "26px"
                             :on-change
                             #(reset! nssym %)]]]
                [ok-cancel donefn cancelfn]]]])))


(defn edit-modal [show?]
  (let [curtab (hmi/get-cur-tab)
        curtid (curtab :id)
        curlabel (curtab :label)
        opts (curtab :opts)
        order (rgt/atom (opts :order))
        eltsper (rgt/atom (str (opts :eltsper)))
        rgap (rgt/atom (-> opts :rgap (cljstr/replace #"px$" "")))
        cgap (rgt/atom (-> opts :cgap (cljstr/replace #"px$" "")))
        size (rgt/atom (opts :size))
        tlabel (rgt/atom curlabel)
        nssym (rgt/atom (str (get-ddb [:tabs :extns curtid :ns])))
        donefn (fn[]
                 (go (async/>! (hmi/get-adb [:main :chans :com])
                               {:label @tlabel :nssym (symbol @nssym)
                                :order @order :eltsper (js/parseInt @eltsper)
                                :rgap (px @rgap) :cgap (px @cgap)
                                :size @size}))
                 (reset! show? false))
        cancelfn (fn[]
                   (go (async/>! (hmi/get-adb [:main :chans :com]) :cancel))
                   (reset! show? false))]

    (fn [show?]
      [modal-panel
       :backdrop-color   "grey"
       :backdrop-opacity 0.4
       :child [v-box
               :gap "10px"
               :children
               [[h-box :gap "10px"
                 :children
                 [[v-box :gap "15px"
                   :children
                   [[label :style {:font-size "18px"} :label "Ordering"]
                    [radio-button
                     :label "Row Ordered"
                     :value :row
                     :model order
                     :label-style (when (= :row @order) {:font-weight "bold"})
                     :on-change #(do (reset! size "auto")
                                     (reset! order %))]
                    [radio-button
                     :label "Column Ordered"
                     :value :col
                     :model order
                     :label-style (when (= :col @order) {:font-weight "bold"})
                     :on-change #(do (reset! size "none")
                                     (reset! order %))] 
                    [h-box :gap "10px"
                     :children [[input-text
                                 :model eltsper
                                 :width "40px" :height "20px"
                                 :on-change #(reset! eltsper %)]
                                [label :label (str "Elts/" (if (= @order :row)
                                                             "row" "col"))]]]]]
                  [v-box :gap "10px"
                   :children
                   [[input-area "Row Gap" rgap]
                    [input-area "Col Gap" cgap]
                    [input-area "Flex size" size]]]]]
                
                [h-box :gap "10px"
                 :children [[label
                             :style {:font-size "18px"}
                             :label "Label"]
                            [input-text
                             :model tlabel
                             :width "200px" :height "26px"
                             :on-change #(reset! tlabel %)]]]
                [h-box :gap "10px"
                 :children [[label
                             :style {:font-size "18px"}
                             :label "Namespace"]
                            [input-text
                             :model nssym
                             :width "200px" :height "26px"
                             :on-change
                             #(reset! nssym %)]]]
                [ok-cancel donefn cancelfn]]]])))



(defn del-modal [show?]
  (let [donefn (fn[]
                 (go (async/>! (hmi/get-adb [:main :chans :com])
                               {:tab2del (hmi/get-cur-tab)}))
                 (reset! show? false))
        cancelfn (fn[]
                   (go (async/>! (hmi/get-adb [:main :chans :com]) :cancel))
                   (reset! show? false))]
    (fn [show?]
      [modal-panel
       :backdrop-color   "grey"
       :backdrop-opacity 0.4
       :child [v-box
               :gap "10px"
               :children
               [[label
                 :style {:font-size "18px"}
                 :label (str "Really Delete: ''"
                             (hmi/get-cur-tab :label) "''?")]
                [ok-cancel donefn cancelfn]]]])))


(defn del-frame-modal [show? info]
  (let [donefn (fn[]
                 (go (async/>! (hmi/get-adb [:main :chans :com]) :ok))
                 (reset! show? false))
        cancelfn (fn[]
                   (go (async/>! (hmi/get-adb [:main :chans :com]) :cancel))
                   (reset! show? false))]
    (fn [show? info]
      (printchan :DEL-FRAME :INFO (info :items) (info :selections))
      [modal-panel
       :backdrop-color   "grey"
       :backdrop-opacity 0.4
       :child [v-box
               :gap "10px"
               :children
               [[selection-list
                 :width "391px"
                 :max-height "95px"
                 :model (info :selections)
                 :choices (info :items)
                 :multi-select? true
                 :on-change #(reset! (info :selections) %)]
                [ok-cancel donefn cancelfn]]]])))


(defn tab-box []
  (let [add-show? (rgt/atom false)
        dup-show? (rgt/atom false)
        del-show? (rgt/atom false)
        ed-show?  (rgt/atom false)
        del-closefn #(do (reset! del-show? false))


        del-frame-show? (rgt/atom false)
        selections (rgt/atom #{})
        del-frame-closefn #(do (reset! del-frame-show? false))]

    (fn []
      [border :padding "2px" :radius "2px"
       :l-border "1px solid lightgrey"
       :r-border "1px solid lightgrey"
       :b-border "1px solid lightgrey"
       :child [h-box
               :gap "10px"
               :children
               [[md-circle-icon-button
                 :md-icon-name "zmdi-plus-circle-o" :size :smaller
                 :tooltip "Add Interactive Tab"
                 :on-click
                 #(go (reset! add-show? true)
                      (let [ch (hmi/get-adb [:main :chans :com])
                            info (async/<! ch)]
                        (when (not= :cancel info)
                          (add-interactive-tab info))))]

                [md-circle-icon-button
                 :md-icon-name "zmdi-plus-circle-o-duplicate" :size :smaller
                 :tooltip "Duplicate Current Tab"
                 :on-click
                 #(go (reset! dup-show? true)
                      (let [ch (hmi/get-adb [:main :chans :com])
                            info (async/<! ch)]
                        (when (not= :cancel info)
                          (duplicate-cur-tab info))))]

                [md-circle-icon-button
                 :md-icon-name "zmdi-minus-square" :size :smaller
                 :tooltip "Delete Frames"
                 :on-click
                 #(go (reset! del-frame-show? true)
                      (let [ch (hmi/get-adb [:main :chans :com])
                            answer (async/<! ch)]
                        (when (not= :cancel answer)
                          (printchan @selections)
                          (doseq [id @selections]
                            (remove-frame id))
                          (reset! selections #{}))))]

                [md-circle-icon-button
                 :md-icon-name "zmdi-undo" :size :smaller
                 :tooltip "Undo last tab operation"
                 :on-click
                 #(do (printchan "Undo") (undo))]
                [md-circle-icon-button
                 :md-icon-name "zmdi-redo" :size :smaller
                 :tooltip "Redo undo operation"
                 :on-click
                 #(do (printchan "Redo") (redo))]

                [md-circle-icon-button
                 :md-icon-name "zmdi-long-arrow-left" :size :smaller
                 :tooltip "Move current tab left"
                 :on-click
                 #(hmi/move-tab (hmi/get-cur-tab :id) :left)]
                [md-circle-icon-button
                 :md-icon-name "zmdi-long-arrow-right" :size :smaller
                 :tooltip "Move current tab right"
                 :on-click
                 #(hmi/move-tab (hmi/get-cur-tab :id) :right)]
                [md-circle-icon-button
                 :md-icon-name "zmdi-edit" :size :smaller
                 :tooltip "Edit current tab"
                 :on-click
                 #(go (reset! ed-show? true)
                      (let [ch (hmi/get-adb [:main :chans :com])
                            info (async/<! ch)]
                        (when (not= :cancel info)
                          (edit-cur-tab info))))]

                [md-circle-icon-button
                 :md-icon-name "zmdi-delete" :size :smaller
                 :tooltip "Delete Current Tab"
                 :on-click
                 #(go (reset! del-show? true)
                      (let [ch (hmi/get-adb [:main :chans :com])
                            info (async/<! ch)]
                        (when (not= :cancel info)
                          (let [{:keys [tab2del]} info
                                tid (tab2del :id)]
                            (printchan :TID tid)
                            (del-tab tid)))))]

                (when @add-show? [add-modal add-show?])
                (when @dup-show? [dup-modal dup-show?])
                (when @ed-show? [edit-modal ed-show?])
                (when @del-show? [del-modal del-show? del-closefn])

                (when @del-frame-show?
                  (let [items (rgt/atom (get-tab-frames))
                        info {:items items :selections selections}]
                    [del-frame-modal del-frame-show? info]))]]])))




;;; Extension Tabs and Wrapfns

(defn vis-panel [inspec donefn] (printchan :vis-panel)
  (go
    (if-let [otchart (get-ddb [:main :otchart])]
      otchart
      (let [nm (hmi/get-adb [:main :uid :name])
            msg {:op :read-clj
                 :data {:session-name nm
                        :render? true
                        :cljstg inspec}}
            _ (hmi/send-msg msg)
            otspec (async/<! (hmi/get-adb [:main :chans :convert]))
            otchart (modal-panel
                     :backdrop-color   "grey"
                     :backdrop-opacity 0.4
                     :child [scroller
                             :max-height "700px"
                             :max-width "1000px"
                             :child [v-box
                                     :gap "10px"
                                     :children [[vgl otspec]
                                                [h-box :gap "5px" :justify :end
                                                 :children
                                                 [[md-circle-icon-button
                                                   :md-icon-name "zmdi-close"
                                                   :tooltip "Close"
                                                   :on-click donefn]]]]]])]
        (update-ddb [:main :otchart] otchart)
        otchart))))

(defn tab<-> [tabval] (printchan "Make TAB<-> called ")
  (let [input (rgt/atom "")
        output (rgt/atom "")
        show? (rgt/atom false)
        alert? (rgt/atom false)
        process-done (fn[event]
                       (reset! show? false)
                       (update-ddb [:main :otspec] :rm
                                   [:main :otchart] :rm))
        process-close (fn[event] (reset! alert? false))]
    (fn [tabval] (printchan "TAB<-> called ")
      [v-box :gap "5px"
       :children
       [[h-box :gap "10px" :justify :between
         :children
         [[h-box :gap "10px"
           :children
           [[gap :size "10px"]
            [md-circle-icon-button
             :md-icon-name "zmdi-circle-o"
             :tooltip "Clear"
             :on-click
             #(do (reset! input ""))]
            [md-circle-icon-button
             :md-icon-name "zmdi-fast-forward"
             :tooltip "Translate VGL -> VG -> Clj"
             :on-click
             #(reset! output
                      (if (= @input "")
                        ""
                        (try
                          (with-out-str
                            (-> (js/JSON.parse @input)
                                js/vl.compile .-spec
                                (js->clj :keywordize-keys true)
                                cljs.pprint/pprint))
                          (catch js/Error e (str e)))))]
            [md-circle-icon-button
             :md-icon-name "zmdi-caret-right-circle"
             :tooltip "Translate JSON to Clj"
             :on-click
             #(reset! output
                      (if (= @input "")
                        ""
                        (try
                          (with-out-str
                            (cljs.pprint/pprint
                             (js->clj (js/JSON.parse @input)
                                      :keywordize-keys true)))
                          (catch js/Error e (str e)))))]]]
          [h-box :gap "10px" :justify :end
           :children
           [[box :child (cond @alert?
                              [alert-panel
                               "Empty specification can't be rendered"
                               process-close]
                              @show?
                              (get-ddb [:main :otchart])
                              :else [p])]
            [md-circle-icon-button
             :md-icon-name "zmdi-caret-left-circle"
             :tooltip "Translate Clj to JSON"
             :on-click
             #(go (reset! input
                          (if (= @output "")
                            ""
                            (let [nm (hmi/get-adb [:main :uid :name])
                                  msg {:op :read-clj
                                       :data {:session-name nm
                                              :render? false
                                              :cljstg @output}}]
                              (hmi/send-msg msg)
                              (async/<! (hmi/get-adb
                                         [:main :chans :convert]))))))]
            [md-circle-icon-button
             :md-icon-name "zmdi-caret-up-circle"
             :tooltip "Render in Popup"
             :on-click #(if (= @output "")
                          (reset! alert? true)
                          (let [ch (vis-panel @output process-done)]
                            (go (async/<! ch)
                                (reset! show? true))))]
            [md-circle-icon-button
             :md-icon-name "zmdi-circle-o"
             :tooltip "Clear"
             :on-click
             #(do (reset! output ""))]
            [gap :size "10px"]]]]]
        [line]
        [h-split
         :panel-1 [box :size "auto"
                   :child [code-mirror input {:name "javascript", :json true}]]
         :panel-2 [box :size "auto"
                   :child [code-mirror output "clojure"]]
         :size    "auto", :width "1050px", :height "600px"]]])))
