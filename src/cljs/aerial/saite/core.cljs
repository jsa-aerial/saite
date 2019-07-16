(ns  aerial.saite.core
  (:require
   [cljs.core.async
    :as async
    :refer (<! >! put! chan)
    :refer-macros [go go-loop]]
   [clojure.string :as cljstr]
   [clojure.set :refer [map-invert]]

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

   [aerial.saite.codemirror
    :as cm
    :refer [code-mirror get-cm-sexpr dbg-cm]]
   [aerial.saite.compiler
    :as comp
    :refer [evaluate expr*!]]

   [cljsjs.mathjax]

   [com.rpl.specter :as sp]

   [reagent.core :as rgt]

   [re-com.core
    :as rcm
    :refer [h-box v-box box border gap line h-split v-split scroller
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




(defn react-hack
  "Monkey patching ... sigh ... This changes the (incorrect) behavior of
  React's removeChild and insertBefore functions which do not properly
  track changes in the DOM as effected by 3rd party libs. MathJax,
  Google Translate, etc. For details see
  `https://github.com/facebook/react/issues/11538`"
  []
  (when (and (fn? js/Node) js/Node.prototype)
    (let [orig-rm-child Node.prototype.removeChild
          new-rm-child
          (fn [child]
            (this-as this
              (if (not= child.parentNode this)
                (do (when js/console
                      (js/console.error
                       "Cannot remove a child from a different parent"
                       child this))
                    child)
                (do #_(printchan this (js-arguments))
                    (.apply orig-rm-child this (js-arguments))))))

          orig-ins-b4 Node.prototype.insertBefore
          new-ins-b4 (fn [newNode referenceNode]
                       (this-as this
                         (if (and referenceNode
                                  (not= referenceNode.parentNode this))
                           (do (when js/console
                                 (js/console.error
                                  "Cannot insert before a reference node "
                                  "from a different parent" referenceNode this))
                               newNode)
                           (do #_(printchan this (js-arguments))
                               (.apply orig-ins-b4 this (js-arguments))))))]
      (set! Node.prototype.removeChild new-rm-child)
      (set! Node.prototype.insertBefore new-ins-b4))))

(react-hack)




;;; Components ============================================================ ;;;


(defn alert-panel [txt closefn]
  (printchan :alert-panel)
  [modal-panel
   :child [re-com.core/alert-box
           :id 1 :alert-type :warning
           :heading txt
           :closeable? true
           :on-close closefn]
   :backdrop-color "grey" :backdrop-opacity 0.0])



;;; CodeMirror components

(defn cm-hiccup [opts input output]
  (let [id (opts :id)
        kwid (-> id name keyword)
        layout (if (= (opts :layout) :up-down) v-box h-box)
        ch (opts :height "400px")
        oh (opts :out-height "100px")]
    [h-box :gap "5px" :attr {:id id}
     :children
     [[v-box :gap "5px"
       :children
       [[md-circle-icon-button
         :md-icon-name "zmdi-caret-right-circle"
         :tooltip "Eval Code"
         :on-click #(printchan :ID id :VID (opts :vid) :eval @input)]
        [md-circle-icon-button
         :md-icon-name "zmdi-circle-o"
         :tooltip "Clear"
         :on-click
         #(do (reset! output ""))]]]
      [layout :gap "5px"
       :width (opts :width "500px")
       :height (+ ch oh 50)
       :children
       [[v-box
         :children
         [[box
           :size (opts :size "auto")
           :width (opts :width "500px")
           :height (opts :height "300px")
           :justify (opts :justify :start)
           :align (opts :justify :stretch)
           :child [code-mirror input "clojure"
                   :cb (fn[m]
                         (let [ostg (with-out-str
                                      (cljs.pprint/pprint
                                       (or (m :value) (m :error))))]
                           (reset! output (str @output
                                               "=> " ostg))
                           (printchan :output @output)))]]]]
        [v-box
         :children
         [[box
           :size (opts :size "auto")
           :width (opts :width "500px")
           :height oh
           :justify (opts :justify :start)
           :align (opts :justify :stretch)
           :child [code-mirror output "clojure"
                   :js-cm-opts {:lineNumbers false,
                                :lineWrapping true}]]]]]]
      [gap :size "10px"]]]))


(comment
  (defn vis! [vid picid]
    (hmi/visualize
     (get-vspec vid)
     (js/document.getElementById picid)))

  (vis! :bc1 "scatter-1")
  (vis! :sqrt "scatter-1")
  (vis! :scatter-1 "scatter-1")

  (when-let [source (get-cm-sexpr @dbg-cm)]
    (evaluate source #(printchan (expr*! %))))
  )


(defn cm []
  (let [input (rgt/atom "")
        output (rgt/atom "")]
    (fn [& opts]
      (let [opts (->> opts (partition-all 2) (mapv vec) (into {}))
            kwid (name (opts :id (gensym "cm-")))
            opts (or (get-adb [:editors kwid :opts])
                     (merge {:id kwid, :size "auto" :layout :up-down
                             :height "300px", :out-height "100px"}
                            opts))
            _ (if (and (opts :src) (= @input "")) (reset! input (opts :src)))
            input (or (get-adb [:editors kwid :in]) input)
            output (or (get-adb [:editors kwid :ot]) output)]
        (when-not (get-adb [:editors kwid])
          (update-adb [:editors kwid] {:in input, :ot output, :opts opts})
          (printchan :CM kwid :called :OPTS opts))
        [cm-hiccup opts input output]))))




;;; Instrumentors

(defn bar-slider-fn [tid val]
  (let [tabval (get-tab-field tid)
        spec-frame-pairs (tabval :spec-frame-pairs)]
    (printchan "Slider update " val)
    (update-tab-field tid :compvis nil)
    (update-tab-field
     tid :spec-frame-pairs
     (mapv (fn[[spec frame]]
             (let [cljspec spec
                   data (mapv (fn[m] (assoc m :b (+ (m :b) val)))
                              (get-in cljspec [:data :values]))
                   newspec (assoc-in cljspec [:data :values] data)]
               [newspec frame]))
           spec-frame-pairs))))

(defn instrumentor [{:keys [tabid spec]}]
  (printchan "Test Instrumentor called" :TID tabid #_:SPEC #_spec)
  (let [cljspec spec
        udata (cljspec :usermeta)]
    (update-adb [:udata] udata)

    (cond
      (not (map? udata)) []

      (udata :slider)
      (let [sval (rgt/atom "0.0")]
        (printchan :SLIDER-INSTRUMENTOR)
        {:top (xform-recom
               (udata :slider)
               246 -10.0
               :m1 sval
               :oc1 #(do (bar-slider-fn tabid %)
                         (reset! sval (str %)))
               :oc2 #(do (bar-slider-fn tabid (js/parseFloat %))
                         (reset! sval %)))})

      :else {}
      )))




;;; Header

(declare
 xform-tab-data get-tab-data
 load-doc
 editor-repl-tab interactive-doc-tab)

(defn file-modal [session-name file-name donefn cancelfn]
  [modal-panel
   :backdrop-color   "grey"
   :backdrop-opacity 0.4
   :child [v-box
           :gap "10px"
           :children [[label
                       :style {:font-size "18px"}
                       :label "Session"]
                      [input-text
                       :model session-name
                       :width "200px" :height "26px"
                       :on-change #(reset! session-name %)]
                      [label
                       :style {:font-size "18px"}
                       :label "File"]
                      [input-text
                       :model file-name
                       :width "200px" :height "26px"
                       :on-change #(reset! file-name %)]
                      [h-box :gap "5px" :justify :end
                       :children
                       [[md-circle-icon-button
                         :md-icon-name "zmdi-check-circle"
                         :tooltip "Choose"
                         :on-click donefn]
                        [md-circle-icon-button
                         :md-icon-name "zmdi-close"
                         :tooltip "Cancel"
                         :on-click cancelfn]]]]]])


(defn input-area [label-txt model]
  [h-box :gap "10px"
   :children [[input-text
               :model model
               :width "60px" :height "20px"
               :on-change #(reset! model %)]
              [label :label label-txt]]])

(defn px [x] (str x "px"))

(defn add-modal [show?]
  (let [edtype (rgt/atom :interactive-doc)
        order (rgt/atom :row)
        eltsper (rgt/atom "1")
        tid (rgt/atom (name (gensym "tab-")))
        tlabel (rgt/atom (cljstr/capitalize @tid))
        advance? (rgt/atom false)
        width (rgt/atom "730")
        height (rgt/atom "700")
        out-height (rgt/atom (if (= @edtype :interactive-doc) "100" "700"))
        size (rgt/atom "auto")
        layout (rgt/atom :up-down)
        donefn (fn[]
                 (go (async/>! (get-adb [:main :com-chan])
                               {:edtype @edtype
                                :id (keyword @tid) :label @tlabel
                                :order @order :eltsper (js/parseInt @eltsper)
                                :width (px @width) :height (px @height)
                                :out-height (px @out-height) :size @size
                                :layout @layout}))
                 (reset! show? false) nil)
        cancelfn (fn[]
                   (go (async/>! (get-adb [:main :com-chan]) :cancel))
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
                     :on-change #(do (reset! layout :up-down)
                                     (reset! out-height "100")
                                     (reset! edtype %))]
                    [radio-button
                     :label "Editor and Output"
                     :value :editor
                     :model edtype
                     :label-style (when (= :editor @edtype)
                                    {:font-weight "bold"})
                     :on-change #(do (reset! layout :left-right)
                                     (reset! out-height "700")
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
                          [input-area "Output Height" out-height]
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
                             :on-change #(reset! tid %)]
                            [gap :size "10px"]
                            [label
                             :style {:font-size "18px"}
                             :label "Label"]
                            [input-text
                             :model tlabel
                             :width "200px" :height "26px"
                             :on-change #(reset! tlabel %)]]]
                [h-box :gap "5px" :justify :end
                 :children
                 [[md-circle-icon-button
                   :md-icon-name "zmdi-check-circle"
                   :tooltip "OK"
                   :on-click donefn]
                  [md-circle-icon-button
                   :md-icon-name "zmdi-close"
                   :tooltip "Cancel"
                   :on-click cancelfn]]]]]
       ])))

(defn ctrl-modal [show?]
  (let [edtype (rgt/atom :none)
        tid (rgt/atom (name (gensym "tab-")))
        tlabel (rgt/atom (cljstr/capitalize @tid))
        donefn (fn[]
                 (go (async/>! (get-adb [:main :com-chan])
                               {:edtype @edtype :id @tid :label @tlabel}))
                 (reset! show? false) nil)
        cancelfn (fn[]
                   (go (async/>! (get-adb [:main :com-chan]) :cancel))
                   (reset! show? false) nil)]
    (fn [show?]
      [modal-panel
       :backdrop-color   "grey"
       :backdrop-opacity 0.4
       :child [v-box
               :gap "10px"
               :children
               [[label :style {:font-size "18px"} :label "Tab List"]
                [scroller
                 :max-height "700px"
                 :max-width "1000px"
                 :child [v-box :gap "10px"
                         :children []]]]]])))

(defn tab-box []
  (let [add-show? (rgt/atom false)
        del-show? (rgt/atom false)
        del-closefn #(do (reset! del-show? false))
        ctrl-show? (rgt/atom false)
        ctrl-donefn (fn[event]
                      #_(go (async/>! (get-adb [:main :com-chan])
                                      {:? :XXX :?? :YYY}))
                      (reset! ctrl-show? false))
        ctrl-cancelfn (fn[event]
                        #_(go (async/>! (get-adb [:main :com-chan]) :cancel))
                        (reset! ctrl-show? false))]
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
                      (let [ch (get-adb [:main :com-chan])
                            info (async/<! ch)]
                        (when (not= :cancel info)
                          (let [{:keys [edtype id label
                                        order eltsper
                                        width height out-height
                                        size layout]} info]
                            (cond
                              (= :converter edtype) (printchan :NYI)

                              (= :editor edtype)
                              (editor-repl-tab
                               id label ""
                               :width width :height height
                               :out-height out-height :layout layout)

                              :else
                              (interactive-doc-tab
                               id label ""
                               :width width :height height
                               :out-height out-height
                               :order order :eltsper eltsper :size size))

                            (printchan info)))))]
                [md-circle-icon-button
                 :md-icon-name "zmdi-minus-circle-outline" :size :smaller
                 :tooltip "Delete Current Tab"
                 :on-click
                 #(go (reset! del-show? true)
                      )]
                [button
                 :label "Tab Control"
                 :tooltip "Change order / Remove Tabs"
                 :on-click #(go)
                 :class "btn-default btn-xs"]

                (when @add-show? [add-modal add-show?])
                (when @del-show? [alert-panel "Really delete?" del-closefn])
                #_(when ctrl-show? [ctrl-modal ctrl-show?])]]])))


(defn saite-header []
  (let[show? (rgt/atom false)
       session-name (rgt/atom "")
       file-name (rgt/atom "")
       donefn (fn[event]
                (go (async/>! (get-adb [:main :com-chan])
                              {:session @session-name :file @file-name}))
                (reset! show? false))
       cancelfn (fn[event]
                  (go (async/>! (get-adb [:main :com-chan]) :cancel))
                  (reset! show? false))]
    (fn []
      [h-box :gap "10px" :max-height "30px"
       :children
       [[gap :size "5px"]
        [:img {:src (get-adb [:main :logo])}]
        [hmi/session-input]
        [gap :size "5px"]
        [title
         :level :level3
         :label [:span.bold (get-adb [:main :uid :name])]]
        [gap :size "30px"]
        [border :padding "2px" :radius "2px"
         :l-border "1px solid lightgrey"
         :r-border "1px solid lightgrey"
         :b-border "1px solid lightgrey"
         :child
         [h-box :gap "10px"
          :children [[md-circle-icon-button
                      :md-icon-name "zmdi-upload" :size :smaller
                      :tooltip "Upload Document"
                      :on-click
                      #(go (let [ch (get-adb [:main :com-chan])]
                             (js/console.log "upload clicked")
                             (reset! session-name (get-adb [:main :uid :name]))
                             (reset! file-name (get-adb [:main :files :load]))
                             (reset! show? true)
                             (let [location (async/<! ch)]
                               (when (not= :cancel location)
                                 (update-adb [:main :files :load]
                                             (location :file))
                                 (hmi/send-msg {:op :load-data :data location})
                                 (load-doc (async/<! ch))))))]

                     [md-circle-icon-button
                      :md-icon-name "zmdi-download" :size :smaller
                      :tooltip "Save Document"
                      :on-click
                      #(go (let [ch (get-adb [:main :com-chan])]
                             (js/console.log "download clicked")
                             (reset! session-name (get-adb [:main :uid :name]))
                             (reset! file-name (get-adb [:main :files :save]))
                             (reset! show? true)
                             (let [location (async/<! ch)]
                               (when (not= :cancel location)
                                 (update-adb [:main :files :save]
                                             (location :file))
                                 (let [spec-info (xform-tab-data
                                                  (get-tab-data))]
                                   (hmi/send-msg
                                    {:op :save-data
                                     :data {:loc location
                                            :info spec-info}}))))))]

                     (when @show?
                       [file-modal session-name file-name
                        donefn cancelfn])] ]]

        [gap :size "20px"]
        [tab-box]]])))




;;; Extension Tabs and Wrapfns

(defn vis-panel [inspec donefn] (printchan :vis-panel)
  (go
    (if-let [otchart (get-adb [:main :otchart])]
      otchart
      (let [nm (get-adb [:main :uid :name])
            msg {:op :read-clj
                 :data {:session-name nm
                        :render? true
                        :cljstg inspec}}
            _ (hmi/send-msg msg)
            otspec (async/<! (get-adb [:main :convert-chan]))
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
        (update-adb [:main :otchart] otchart)
        otchart))))

(defn tab<-> [tabval] (printchan "Make TAB<-> called ")
  (let [input (rgt/atom "")
        output (rgt/atom "")
        show? (rgt/atom false)
        alert? (rgt/atom false)
        process-done (fn[event]
                       (reset! show? false)
                       (update-adb [:main :otspec] :rm
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
                              (get-adb [:main :otchart])
                              :else [p])]
            [md-circle-icon-button
             :md-icon-name "zmdi-caret-left-circle"
             :tooltip "Translate Clj to JSON"
             :on-click
             #(go (reset! input
                          (if (= @output "")
                            ""
                            (let [nm (get-adb [:main :uid :name])
                                  msg {:op :read-clj
                                       :data {:session-name nm
                                              :render? false
                                              :cljstg @output}}]
                              (hmi/send-msg msg)
                              (async/<! (get-adb [:main :convert-chan]))))))]
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


(defn ^:export editor-repl-tab
  [tid label src & {:keys [width height out-height layout]
                    :or {width "730px"
                         height "700px"
                         out-height "700px"
                         layout :left-right}}]
  (let [cmfn (cm)
        eid (str "ed-" (name tid))
        uinfo {:fn ''editor-repl-tab
               :tid tid
               :eid eid
               :width width
               :height height
               :out-height out-height
               :layout layout
               :src src}]
    (update-adb [:tabs :extns tid] uinfo)
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
  [tid label src & {:keys [width height out-height specs order eltsper size]
                    :or {width "730px"
                         height "700px"
                         out-height "100px"
                         specs []
                         order :row eltsper 1 size "auto"}}]
  (let [cmfn (cm)
        eid (str "ed-" (name tid))
        uinfo {:fn ''interactive-doc-tab
               :tid tid
               :eid eid
               :width width
               :height height
               :out-height out-height
               :src src}]
    (update-adb [:tabs :extns tid] uinfo)
    (add-tab
     {:id tid
      :label label
      :specs specs
      :opts {:order order, :eltsper eltsper, :size size
             :wrapfn (fn[hcomp]
                       [h-split
                        :panel-1 [cmfn :id eid
                                  :width width
                                  :height height
                                  :out-height out-height
                                  :src src]
                        :panel-2 [scroller
                                  :max-height "800px"
                                  :max-width "1200px"
                                  :align :start
                                  :child hcomp]
                        :initial-split "33%"
                        :width "2048px"])}})
    (let [opts (hmi/get-tab-field tid :opts)
          s-f-pairs (hmi/make-spec-frame-pairs tid opts specs)]
      (hmi/update-tab-field tid :compvis (hmi/vis-list tid s-f-pairs opts)))))


(def extns-xref
  (into {} (map vector
                '[editor-repl-tab interactive-doc-tab]
                [editor-repl-tab interactive-doc-tab])))




;;; Save and Restore ===================================================== ;;;

(defn tab-data []
  (->> (sp/select [sp/ATOM :tabs :active sp/ATOM sp/ALL]
                  hmi/app-db)
       (map (fn[tab]
              (select-keys tab [:id :label :opts :specs])))))

(defn get-extn-info [tid]
  (let [m (get-adb [:tabs :extns tid])
        eid (m :eid)
        src (get-adb [:editors eid :in])]
    (assoc m :src (deref src))))

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


(defn load-doc [doc-data]
  (let [tids (rest (mapv :id (tab-data)))]
    (doseq [tid tids] (hmi/del-tab tid))
    (->> doc-data deref
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
                       (apply f tid label src args)
                       [tid :done])
                     (do (->> m vals first :specs hmi/update-tabs)
                         [(-> m keys first) :done]))))))))




;;; Messaging ============================================================ ;;;


(defmethod user-msg :data [msg]
  (printchan :DATA msg))


(def newdoc-data (atom []))
(defmethod user-msg :load-data [msg]
  (let [data (msg :data)
        ch (get-adb [:main :com-chan])]
    (reset! newdoc-data data)
    (go (async/>! ch newdoc-data))))


(defmethod user-msg :clj-read [msg]
  (let [data (msg :data)
        render? (data :render?)
        clj (dissoc data :render?)
        result (if render?
                 clj
                 (try (-> clj clj->js (js/JSON.stringify nil, 2))
                      (catch js/Error e (str e))))
        ch (get-adb [:main :convert-chan])]
    #_(printchan render? result)
    (go (async/>! ch result))))


(defmethod user-msg :app-init [msg]
  (update-adb [:main :convert-chan] (async/chan)
              [:main :com-chan] (async/chan)
              [:main :files :save] "session.clj"
              [:main :files :load] "session.clj"
              [:editors] {})
  (add-tab {:id :xvgl
            :label "<->"
            :opts {:extfn (tab<-> :NA)}}))


(defn update-data
  "Originally meant as general updater of vis plot/chart data
  values. But to _render_ these, requires knowledge of the application
  pages/structure. So, this is not currently used. If we can figure
  out Vega chageSets and how they update, we may be able to make this
  a general op in Hanami."
  [data-maps]
  (printchan :UPDATE-DATA data-maps)
  #_(mapv (fn [{:keys [usermeta data]}]
          (let [vid (usermeta :vid)
                spec (dissoc (get-vspec vid) :data)]
            (assoc-in spec [:data :values] data)))
        data-maps))

(defmethod user-msg :data [msg]
  (update-data (msg :data)))




;;; Call Backs ============================================================ ;;;

#_(js/MathJax.Hub.Queue #js ["Typeset" js/MathJax.Hub])
#_(js/MathJax.Hub.Queue
   #js ["Typeset" js/MathJax.Hub
        (js/document.getElementById (get-adb [:mathjax]))])

(def mathjax-chan (async/chan 10))

(defn mathjax-put [fid]
  (go (async/>! mathjax-chan (name fid))))

(go-loop [id (async/<! mathjax-chan)]
  (let [elt (js/document.getElementById id)
        _ (printchan :ID id, :ELT elt)
        jsvec (when elt (clj->js ["Typeset" js/MathJax.Hub elt]))]
    (async/<! (async/timeout 50))
    (cond
      ::global (js/MathJax.Hub.Queue #js ["Typeset" js/MathJax.Hub])
      jsvec    (js/MathJax.Hub.Queue jsvec)
      :else    (async/>! mathjax-chan id)))
  (recur (async/<! mathjax-chan)))


(defn frame-callback
  ([]
   (go (async/>! mathjax-chan ::global)))
  ([spec frame] (printchan :FRAME-CALLBACK :spec spec)
   (let [id (frame :frameid)]
     (go (async/>! mathjax-chan id))
     [spec frame])))


(defn symxlate-callback [sym]
  (let [snm (name sym)]
    (cond ;; (= snm "md") md
          (= snm "cm") (cm)
          :else sym)))


;;; Startup ============================================================== ;;;


#_(when-let [elem (js/document.querySelector "#app")]
  (hc/update-defaults
     :USERDATA {:tab {:id :TID, :label :TLBL, :opts :TOPTS}
                :frame {:top :TOP, :bottom :BOTTOM,
                        :left :LEFT, :right :RIGHT
                        :fid :FID}
                :opts :OPTS
                :vid :VID,
                :msgop :MSGOP,
                :session-name :SESSION-NAME}
     :MSGOP :tabs, :SESSION-NAME "Exploring"
     :TID :expl1, :TLBL #(-> :TID % name cljstr/capitalize)
     :OPTS (hc/default-opts :vgl), :TOPTS (hc/default-opts :tab))
  (start :elem elem
         :port js/location.port
         :symxlate-cb symxlate-callback
         :frame-cb frame-callback
         :header-fn saite-header
         :instrumentor-fn instrumentor))



(comment

  (when-let [elem (js/document.querySelector "#app")]
    (hc/update-defaults
     :USERDATA {:tab {:id :TID, :label :TLBL, :opts :TOPTS}
                :frame {:top :TOP, :bottom :BOTTOM,
                        :left :LEFT, :right :RIGHT
                        :fid :FID}
                :opts :OPTS
                :vid :VID,
                :msgop :MSGOP,
                :session-name :SESSION-NAME}
     :MSGOP :tabs, :SESSION-NAME "Exploring"
     :TID :expl1, :TLBL #(-> :TID % name cljstr/capitalize)
     :OPTS (hc/default-opts :vgl), :TOPTS (hc/default-opts :tab))
    (start :elem elem
           :port 3000
           :symxlate-cb symxlate-callback
           :frame-cb frame-callback
           :header-fn saite-header
           :instrumentor-fn instrumentor))


  ;; Editor only example
  (editor-repl-tab :editor "ClJS" "")


  ;; Chapter example
  (interactive-doc-tab :chap1 "Chapter 1" ch1src)

  (def ch1src
    "
12345678901234567890123456789012345678901234567890123456789012345678901234567890
;; Add some page/section title text
(add-to-tab-body
 :chap1
 (hc/xform
  ht/empty-chart :FID :dtitle
  :TOP '[[gap :size \"50px\"]
         [md \"# Example Interactive Document Creation\"]]))


;; Render a graphic in it
(add-to-tab-body
 :chap1
 (hc/xform
  ht/bar-chart
  :TITLE \"Top headline phrases\"
  :TID :chap1 :FID :dhline :ELTSPER 1
  :X :x-value :Y :y-value :YTYPE \"nominal\"
  :DATA
  [{:x-value 8961 :y-value \"will make you\"}
   {:x-value 4099 :y-value \"this is why\"}
   { :x-value 3199 :y-value \"can we guess\"}
   {:x-value 2398 :y-value \"only X in\"}
   {:x-value 1610 :y-value \"the reason is\"}
   {:x-value 1560 :y-value \"are freaking out\"}
   {:x-value 1425 :y-value \"X stunning photos\"}
   {:x-value 1388 :y-value \"tears of joy\"}
   {:x-value 1337 :y-value \"is what happens\"}
   {:x-value 1287 :y-value \"make you cry\"}]))


;; Add empty picture frame with all elements as MD
(add-to-tab-body
 :chap1
 (hc/xform
  ht/empty-chart :FID :dwrup1
  :TOP '[[gap :size \"200px\"] [md \"# Header top\"]]
  :BOTTOM '[[gap :size \"200px\"] [md \"# Header bottom\"]]
  :LEFT '[[gap :size \"50px\"] [md \"### Header left\"]]
  :RIGHT '[[md \"### Header right\"]]))

;; Remove empty frame
(remove-from-tab-body :chap1 :dwrup1)


;; Make some distribution data
(def obsdist
  (let [obs [[0 9] [1 78] [2 305] [3 752] [4 1150] [5 1166]
             [6 899] [7 460] [8 644] [9 533] [10 504]]
        totcnt (->> obs (mapv second) (apply +))
        pdist (map (fn[[k cnt]] [k (double (/ cnt totcnt))]) obs)]
    pdist))


;; Add a distribution chart to the tab
(add-to-tab-body
 :chap1
 (hc/xform
  ht/layer-chart
  :TID :chap1 :FID :dex1
  :TITLE \"A Real (obvserved) distribution with incorrect simple mean\"
  :HEIGHT 400 :WIDTH 450
  :LAYER
  [(hc/xform ht/bar-layer :XTITLE \"Count\" :YTITLE \"Probability\")
   (hc/xform ht/xrule-layer :AGG \"mean\")]
  :DATA (mapv (fn[[x y]] {:x x :y y :m 5.7}) obsdist)))


;; Add some 'writeup'
(add-to-tab-body
 :chap1
 (hc/xform
  ht/empty-chart :FID :dwrup2
  :TOP '[[gap :size \"50px\"]
         [md \"# Fixed distribution\\n
Here we have corrected the mean by properly including item weights\"]]))


(add-to-tab-body
 :chap1
 (hc/xform
  ht/layer-chart
  :TID :chap1 :FID :dex2
  :TITLE \"The same distribution with correct weighted mean\"
  :HEIGHT 400 :WIDTH 450
  :LAYER
  [(hc/xform ht/bar-layer :XTITLE \"Count\" :YTITLE \"Probability\")
   (hc/xform ht/xrule-layer :X \"m\")]
  :DATA (mapv (fn[[x y]] {:x x :y y :m 5.7}) obsdist)))


(defn update-frame [tid fid element content]
  (hmi/update-frame tid fid element content)
  (aerial.saite.core/mathjax-put fid))

(update-frame
  :chap1 :dwrup1
  :bottom [[h-box
           :children
           [[gap :size \"50px\"]
            #_[md \"* title\"]
            #_[p \"some text\"]
            [md {:style {:font-size \"20px\" :color \"blue\"}}
             \"
  * P(x) = \\\\(\\\\frac{1}{\\\\sqrt{2\\\\pi \\\\sigma^2}} e^{- \\\\frac{(x - \\\\mu)^2}{2\\\\sigma ^2}}\\\\)

  * \\\\(f(x) = x^2\\\\)\"]]]])

")



  (let [msg {:op :save-data
             :data (xform-tab-data (get-tab-data))}]
    (hmi/send-msg msg))

  (hmi/send-msg {:op :load-data :data (get-adb [:main :uid :name])})




  (->> newdoc-data deref
       (mapv (fn[m]
               (let [tm (->> m vals first)]
                 (if-let [info (->> tm :opts :wrapfn)]
                   (let [f (-> info :fn second)
                         {:keys [tid src]} info
                         label (tm :label)
                         specs (tm :specs)
                         args (->> (dissoc info :fn :tid :src)
                                   seq (cons [:specs specs])
                                   (apply concat))]
                     `(apply ~f ~tid ~label ~src ~args))
                   m)))))

  (let [src (prn-str (xform-tab-data (get-tab-data)))
        res (atom {})]
    (aerial.saite.compiler/evaluate src (fn[x] (reset! res x)))
    (if-let [v (:value @res)]
      v
      @res))

  (let [src (prn-str (get-tab-data))
        res (atom {})]
    (aerial.saite.compiler/evaluate src (fn[x] (reset! res  (x :value))))
    (get-in @res [0 :chap1 :opts :wrapfn :fn]))





  (let[frame (js/document.getElementById "f1")]
    (js/console.log (aget frame.childNodes 2)))

  (let[frame (js/document.getElementById "f1")
       bottom (aget frame.childNodes 4)]
    (js/console.log bottom.childNodes))

  (let [children `[h-box [md "### title"] [p "some text"]]]
    (js/console.log (rgt/as-element children)))

  (let [children `[h-box [md "### title"] [p "some text"]]
        div (js/document.createElement "div")]
    (rgt/render children div)
    (js/console.log (aget div.childNodes 0)))




  (def loaded? (r/atom false))

  (.addEventListener js/document "load" #(reset! loaded? true))

  (defn app []
    [:div {:class (when @loaded? "class-name")}])




  (def CM-VALS
    (sp/recursive-path
     [] p
     (sp/cond-path #(and (vector? %)
                         (and (-> % first symbol?)
                              (= (-> % first name) "cm")))
                   sp/STAY
                   vector? [sp/ALL p])))

  (let [hiccup (sp/transform
                [sp/ALL CM-VALS]
                (fn[cm]
                  (let [m (->> cm rest (partition-all 2) (mapv vec) (into {}))
                        id (m :id)
                        ed (get-adb [:editors id])
                        instg (deref (ed :in))
                        otstg (deref (ed :ot))
                        opts (merge (ed :opts) {:instg instg :otstg otstg} m)]
                    [(-> cm first name symbol) ::opts opts]))
                '[[aerial.saite.examples/gap :size "10px"]
                  [aerial.saite.examples/cm :id "cm-scatter-1"]])]
    [(->> hiccup second first (= 'cm))
     (->> hiccup second second (= ::opts))])

  (sp/select [sp/ALL CM-VALS] '[[aerial.saite.examples/gap :size "10px"]
                                [aerial.saite.examples/cm :id "cm-scatter-1"]])

  cljs.reader/read-string

  (def paratxt2 (js/document.createTextNode "\\(f(x) = \\sqrt x \\)"))
  (def para (js/document.createElement "P"))
  (.appendChild para paratxt2)
  (do
    (.replaceChild divelt para (aget divelt.childNodes 0))
    (js/MathJax.Hub.Queue #js ["Typeset" js/MathJax.Hub para]))

  (js/document.createElement "a")

  )
