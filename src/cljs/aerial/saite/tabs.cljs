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
    :as sr
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
  [tid label src & {:keys [width height out-width out-height
                           layout ed-out-order ns file]
                    :or {width "730px"
                         height "700px"
                         out-width "730px"
                         out-height "700px"
                         layout :left-right
                         ed-out-order :first-last
                         ns 'aerial.saite.usercode
                         file nil}}]
  (let [cmfn (cm)
        eid (str "ed-" (name tid))
        uinfo {:fn ''editor-repl-tab
               :tid tid
               :eid eid
               :width width
               :height height
               :out-width out-width
               :out-height out-height
               :layout layout
               :ed-out-order ed-out-order
               :ns ns
               :file file
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
                        :child [cmfn :tid tid :id eid
                                :width width
                                :height height
                                :out-width out-width
                                :out-height out-height
                                :layout layout
                                :ed-out-order ed-out-order
                                :src src]
                        :width "2100px" :height out-height])}})))


(defn ^:export interactive-doc-tab
  [tid label src & {:keys [width height out-width out-height
                           ed-out-order $split md-defaults
                           ns file cmfids specs order eltsper rgap cgap size]
                    :or {width "730px"
                         height "700px"
                         out-width "730px"
                         out-height "100px"
                         ed-out-order :last-first
                         $split (get-ddb [:tabs :extns :$split])
                         ns 'aerial.saite.usercode
                         file nil
                         cmfids {:cm 0 :fm 0}
                         specs []
                         order :row eltsper 1 :rgap "20px" :cgap "20px"
                         size "auto"}}]
  (let [cmfn (cm)
        eid (str "ed-" (name tid))
        maxh (or (get-ddb [:main :interactive-tab :doc :max-height]) "900px")
        maxw (or (get-ddb [:main :interactive-tab :doc :max-width]) "2000px")
        sratom (rgt/atom $split)
        uinfo {:fn ''interactive-doc-tab
               :tid tid
               :eid eid
               :width width
               :height height
               :out-width out-width
               :out-height out-height
               :ed-out-order ed-out-order
               :$split $split
               :$sratom sratom
               :ns ns
               :file file
               :cmfids cmfids
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
              :width "2100px" :height maxh)]
    (set-namespace ns)
    (update-ddb [:tabs :extns tid] uinfo)
    (when md-defaults (update-ddb [:tabs :md-defaults tid] md-defaults))
    (add-tab
     {:id tid
      :label label
      :file file
      :specs specs
      :opts {:order order, :eltsper eltsper,
             :rgap rgap :cgap cgap :size size
             :wrapfn (fn[hcomp]
                       [hsfn
                        :panel-1 [cmfn :tid tid :id eid
                                  :width width
                                  :height height
                                  :out-height out-height
                                  :out-width out-width
                                  :ed-out-order ed-out-order
                                  :src src]
                        :panel-2 [scroller
                                  :max-height maxh
                                  :max-width maxw
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




;;; Header Misc Component ================================================= ;;;

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

(def theme-names
  ["3024-day" "3024-night" "abcdef" "ambiance" "ayu-dark" "ayu-mirage"
   "base16-dark" "base16-light" "bespin" "blackboard" "cobalt" "colorforth"
   "darcula" "dracula" "duotone-dark" "duotone-light" "eclipse" "elegant"
   "erlang-dark" "gruvbox-dark" "hopscotch" "icecoder" "idea" "isotope"
   "lesser-dark" "liquibyte" "lucario" "material" "material-darker"
   "material-palenight" "material-ocean" "mbo" "mdn-like" "midnight" "monokai"
   "moxer" "neat" "neo" "night" "nord" "oceanic-next" "panda-syntax"
   "paraiso-dark" "paraiso-light" "pastel-on-dark" "railscasts" "rubyblue"
   "seti" "shadowfox" "solarized dark" "solarized light" "the-matrix"
   "tomorrow-night-bright" "tomorrow-night-eighties" "ttcn" "twilight"
   "vibrant-ink" "xq-dark" "xq-light" "yeti" "yonce" "zenburn"])

(def themes (->> theme-names (mapv vector (map keyword theme-names)) (into {})))

(defn theme-modal [theme? theme]
  (let [choices (->> themes (sort-by second)
                     (mapv (fn[[k v]] {:id k :label v})))
        cancelfn (fn[event] (reset! theme? false))
        donefn #(let [tid (hmi/get-cur-tab :id)
                      cms (->> (get-ddb [:tabs :cms tid])
                               vals (mapv vec) flatten
                               rest (take-nth 2))]
                  (doseq [cm cms] (cm/set-theme cm (themes @theme)))
                  (reset! theme? false))]
    (fn[theme? theme]
      [modal-panel
       :backdrop-color   "grey"
       :backdrop-opacity 0.4
       :child [v-box :gap "10px"
               :children
               [[label :style {:font-size "18px"} :label "Themes"]
                [single-dropdown
                 :choices choices
                 :model theme
                 :width "300px"
                 :on-change #(reset! theme %)]
                [ok-cancel donefn cancelfn]]]])))


(defn help-box []
  (let [quick? (rgt/atom false)
        theme? (rgt/atom false)
        theme (rgt/atom nil)]
    (fn []
      [h-box :gap "5px" :justify :end
       :children
       [[md-circle-icon-button
         :md-icon-name "zmdi-brush" :size :smaller
         :tooltip "Theme"
         :on-click #(reset! theme? true)]
        [md-circle-icon-button
         :md-icon-name "zmdi-help" :size :smaller
         :tooltip "Quick Help"
         :on-click #(reset! quick? true)]
        [md-circle-icon-button
         :md-icon-name "zmdi-info" :size :smaller
         :tooltip "Doc Help"
         :on-click #()]
        (when @quick? [help-modal quick?])
        (when @theme?
          (reset! theme (keyword (get-ddb [:main :editor :theme])))
          [theme-modal theme? theme])
        [gap :size "10px"]]])))




;;; Header Tab Mgt Components ============================================= ;;;

(defn del-tab [tid]
  (let [x (sp/select-one [sp/ATOM :tabs :active sp/ATOM] hmi/app-db)
        eid (get-ddb [:tabs :extns tid :eid])]
    (push-undo x)
    (update-ddb [:tabs :extns tid] :rm
                [:tabs :cms tid]   :rm
                [:editors tid eid] :rm)
    (hmi/del-vgviews tid)
    (hmi/del-tab tid)))

(defn add-interactive-tab [info-map]
  (let [x (sp/select-one [sp/ATOM :tabs :active sp/ATOM] hmi/app-db)
        {:keys [edtype ns id label
                order eltsper
                width height out-width out-height
                size rgap cgap layout ed-out-order]} info-map]
    (push-undo x)
    (cond
      (= :converter edtype) (printchan :NYI)

      (= :editor edtype)
      (editor-repl-tab
       id label "" :ns ns
       :width width :height height
       :out-width out-width :out-height out-height
       :layout layout :ed-out-order ed-out-order)

      :else
      (interactive-doc-tab
       id label "" :ns ns
       :width width :height height
       :out-width out-width :out-height out-height
       :ed-out-order ed-out-order
       :order order :eltsper eltsper
       :rgap rgap :cgap cgap :size size))

    (printchan info-map)))


(defn duplicate-cur-tab [info]
  (let [{:keys [tid label nssym]} info
        ctid (hmi/get-cur-tab :id)
        uinfo (or (get-ddb [:tabs :extns ctid]) {:fn [:_ :NA]})
        eid (str "ed-" (name tid))
        {:keys [width height out-width out-height
                layout ed-out-order cmfids]} uinfo
        cmfids (if cmfids cmfids {:cm 0 :fm 0})
        src (deref (get-ddb [:editors ctid (uinfo :eid) :in]))
        tabval (hmi/get-tab-field ctid)
        {:keys [specs opts]} tabval
        specs (mapv #(update-in %1 [:usermeta :tab :id] (fn[_] tid)) specs)
        {:keys [order eltsper size rgap cgap]} opts
        edtype (second (uinfo :fn))]
    (case edtype
      :NA
      (add-tab
       {:id tid :label label :specs specs :opts opts})

      editor-repl-tab
      (editor-repl-tab
       tid label src :ns nssym
       :width width :height height
       :out-width out-width :out-height out-height
       :layout layout :ed-out-order ed-out-order)

      interactive-doc-tab
      (interactive-doc-tab
       tid label src :ns nssym :cmfids cmfids
       :width width :height height
       :out-width out-width :out-height out-height
       :ed-out-order ed-out-order
       :specs specs :order order :eltsper eltsper :size size
       :rgap rgap :cgap cgap))))


(defn edit-cur-tab [info]
  (let [curtab (hmi/get-cur-tab)
        tid (curtab :id)
        label (curtab :label)
        opts (curtab :opts)
        rgap? (opts :rgap)
        specs (curtab :specs)
        {:keys [label nssym width height out-width out-height]} info
        newextn-info (merge (get-ddb [:tabs :extns tid])
                            {:width width :height height
                             :out-width out-width :out-height out-height
                             :ns nssym})
        newopts (merge opts (dissoc info
                                    :label :nssym
                                    :width :height :out-width :out-height))
        newopts (if rgap?
                  newopts
                  (dissoc newopts :order :eltsper :rgap :cgap :size))
        s-f-pairs (when rgap? (hmi/make-spec-frame-pairs tid newopts specs))]
    (update-ddb [:tabs :extns tid] newextn-info)
    (hmi/update-tab-field tid :opts newopts)
    (hmi/update-tab-field tid :label label)
    (if rgap?
      (hmi/update-tab-field
       tid :compvis (hmi/vis-list tid s-f-pairs newopts))
      #_(do (del-tab tid)
          (editor-repl-tab tid label)))))



(defn file-new [code? session-name file-name donefn cancelfn]
  [v-box
   :gap "10px"
   :children [[label
               :style {:font-size "18px"}
               :label (if code? "Directory" "Session")]
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

(defn save-charts [donefn cancelfn]
  [v-box
   :gap "10px"
   :children [[label
               :style {:font-size "18px"}
               :label "Save all visualizations as PNGs?"]
              [ok-cancel donefn cancelfn]]])

(defn save-all-tab-files [donefn cancelfn]
  [v-box
   :gap "10px"
   :children [[label
               :style {:font-size "18px"}
               :label "Save all main editors of tabs with associated files?"]
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

(defn get-all-tab-files []
  {:all? true
   :files
   (->> (keys (get-ddb [:tabs :extns]))
        (keep (fn [tid]
                (let [tab (get-ddb [:tabs :extns tid])
                      file (get-ddb [:tabs :extns tid :file])]
                  (when file
                    [tid file])))))})

(defn file-modal
  [choices session-name file-name mode url charts all donefn cancelfn]
  (let [sessions (rgt/atom (->> choices deref keys sort
                                (mapv (fn[k] {:id k :label k}))))
        doc-files  (rgt/atom (->> session-name deref (#(@choices %))
                                  (mapv (fn[k] {:id k :label k}))))
        url? (rgt/atom false)
        urldonefn (fn[event] (reset! url? false) (donefn event))

        docmodes #{:load :save}
        codemodes #{:getcode :savecode}
        savemodes #{:save :savecode}

        charts? (rgt/atom false)
        chartdonefn (fn [event]
                      (reset! charts true)
                      (reset! charts? false)
                      (donefn event))

        all? (rgt/atom false)
        alldonefn (fn [event]
                    (reset! all (get-all-tab-files))
                    (reset! all? false)
                    (donefn event))

        new? (rgt/atom false)
        newdonefn (fn[event]
                    (let [fname @file-name
                          newsession? (not (some (fn[x]
                                                   (= (x :label) @session-name))
                                                 @sessions))
                          names (if newsession?
                                  [fname]
                                  (->> doc-files deref (map :id) (cons fname)))
                          newdfs (->> names sort
                                      (mapv (fn[k] {:id k :label k})))]
                      (reset! doc-files newdfs)
                      (reset! choices (assoc @choices
                                             @session-name (vec names)))
                      (reset! sessions (->> choices deref keys sort
                                            (mapv (fn[k] {:id k :label k}))))
                      (printchan @doc-files @choices)
                      (reset! new? false)))]
    (fn [choices session-name file-name mode url charts all donefn cancelfn]
      [modal-panel
       :backdrop-color   "grey"
       :backdrop-opacity 0.4
       :child [v-box
               :gap "10px"
               :children
               [(when (savemodes @mode)
                  [h-box :gap "10px"
                   :children [(when (and (not @charts?) (not @all?))
                                [checkbox
                                 :model new?
                                 :label "New location"
                                 :on-change #(reset! new? %)])
                              (when (docmodes @mode)
                                [checkbox
                                 :model charts?
                                 :label "Visualizations"
                                 :on-change #(reset! charts? %)])
                              (when (codemodes @mode)
                                [checkbox
                                 :model all?
                                 :label "All tab files"
                                 :on-change #(reset! all? %)])]])
                (when (not (savemodes @mode))
                  [checkbox
                   :model url?
                   :label "URL"
                   :on-change #(reset! url? %)])
                (cond
                  @new? [file-new (codemodes @mode) session-name file-name
                         newdonefn cancelfn]

                  @url? [urldoc url urldonefn cancelfn]

                  @charts? [save-charts chartdonefn cancelfn]

                  @all? [save-all-tab-files alldonefn cancelfn]

                  :else
                  [v-box
                   :gap "10px"
                   :children
                   [[label
                     :style {:font-size "18px"}
                     :label (if (codemodes @mode) "Directory" "Session")]
                    [single-dropdown
                     :choices @sessions
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
  (let [nondefault-etabs (dissoc (get-ddb [:tabs :extns])
                                 :$split :scratch :xvgl)
        i (inc (count nondefault-etabs))
        [tx lx] (if (= edtype :editor)
                  ["ed" "Editor "]
                  ["chap" "Chapter "])
        [tx lx] ["tab" "Tab"]
        tid (str tx i)
        label (str lx i)]
    [tid label]))

(defn add-modal [show?]
  (let [defaults (get-ddb [:main :interactive-tab])
        ed-defaults (get-ddb [:main :editor])

        edtype (rgt/atom (defaults :edtype :interactive-doc))
        order (rgt/atom (defaults :order :row))
        eltsper (rgt/atom (defaults :eltsper "1"))
        rgap (rgt/atom (defaults :rgap "20"))
        cgap (rgt/atom (defaults :cgap "20"))
        [tx lx] (next-tid-label @edtype)
        tid (rgt/atom tx)
        tlabel (rgt/atom lx)
        nssym (rgt/atom (defaults :nssym "doc.code"))
        size (rgt/atom (defaults :size "auto"))
        layout (rgt/atom (defaults :layout :up-down))
        ed-out-order (rgt/atom (defaults :ed-out-order :first-last))

        edoutsz (get-in ed-defaults [:size :edout])
        eddocsz (get-in ed-defaults [:size :eddoc])
        edsize (rgt/atom (if (= @edtype :interactive-doc) eddocsz edoutsz))
        width (rgt/atom (@edsize :width "730"))
        height (rgt/atom (@edsize :height "500"))
        out-width (rgt/atom (@edsize :out-width "730"))
        out-height (rgt/atom (@edsize :out-height
                              (if (= @edtype :interactive-doc) "100" "700")))
        advance? (rgt/atom false)
        donefn (fn[]
                 (go (async/>! (hmi/get-adb [:main :chans :com])
                               {:edtype @edtype :ns (symbol @nssym)
                                :id (keyword @tid) :label @tlabel
                                :order @order :eltsper (js/parseInt @eltsper)
                                :rgap (px @rgap) :cgap (px @cgap)
                                :width (px @width) :height (px @height)
                                :out-width  (px @out-width)
                                :out-height (px @out-height)
                                :size @size
                                :layout @layout :ed-out-order @ed-out-order}))
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
                                   (reset! edsize eddocsz)
                                   (reset! layout :up-down)
                                   (reset! ed-out-order :first-last)
                                   (reset! width (@edsize :width))
                                   (reset! height (@edsize :height))
                                   (reset! out-width (@edsize :out-width))
                                   (reset! out-height (@edsize :out-height))
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
                                   (reset! edsize edoutsz)
                                   (reset! layout :left-right)
                                   (reset! ed-out-order :first-last)
                                   (reset! width (@edsize :width))
                                   (reset! height (@edsize :height))
                                   (reset! out-width (@edsize :out-width))
                                   (reset! out-height (@edsize :out-height))
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
                          [input-area "Output Width" out-width]
                          [input-area "Output Height" out-height]]]
                        [v-box :gap "10px"
                         :children
                         [[input-area "Row Gap" rgap]
                          [input-area "Col Gap" cgap]
                          [input-area "Flex size" size]]]
                        [v-box :gap "10px"
                         :children
                         [[label :label "Editor / Output Layout"]
                          [h-box :gap "10px"
                           :children
                           [[radio-button
                             :label "Left-Right"
                             :value :left-right
                             :model layout
                             :label-style (when (= :left-right @layout)
                                            {:font-weight "bold"})
                             :on-change #(reset! layout %)]
                            [radio-button
                             :label "First-Last"
                             :value :first-last
                             :model ed-out-order
                             :label-style (when (= :first-last @ed-out-order)
                                            {:font-weight "bold"})
                             :on-change #(reset! ed-out-order %)]]]
                          [h-box :gap "10px"
                           :children
                           [[radio-button
                             :label "Up-Down"
                             :value :up-down
                             :model layout
                             :label-style (when (= :up-down @layout)
                                            {:font-weight "bold"})
                             :on-change #(reset! layout %)]
                            [radio-button
                             :label "Last-First"
                             :value :last-first
                             :model ed-out-order
                             :label-style (when (= :last-first @ed-out-order)
                                            {:font-weight "bold"})
                             :on-change #(reset! ed-out-order %)]]]]]]])]]
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
        rgap? (opts :rgap)
        rgap (rgt/atom (when rgap? (-> opts :rgap (cljstr/replace #"px$" ""))))
        cgap (rgt/atom (when rgap? (-> opts :cgap (cljstr/replace #"px$" ""))))
        size (rgt/atom (opts :size))
        ed-out-order (rgt/atom (opts :ed-out-order))

        {:keys [width height
                out-width out-height]} (or (get-ddb [:tabs :extns curtid])
                                           ;; Incase this is a std tab!!
                                           {:width "0px" :height "0px"
                                            :out-width "0px" :out-height "0px"})
        width (rgt/atom (cljstr/replace width #"px$" ""))
        height (rgt/atom (cljstr/replace height #"px$" ""))
        out-width (rgt/atom (cljstr/replace out-width #"px$" ""))
        out-height (rgt/atom (cljstr/replace out-height #"px$" ""))

        tlabel (rgt/atom curlabel)
        nssym (rgt/atom (str (get-ddb [:tabs :extns curtid :ns])))
        donefn (fn[]
                 (go (async/>! (hmi/get-adb [:main :chans :com])
                               {:label @tlabel :nssym (symbol @nssym)
                                :order @order :eltsper (js/parseInt @eltsper)
                                :rgap (px @rgap) :cgap (px @cgap)
                                :width (px @width) :height (px @height)
                                :out-width  (px @out-width)
                                :out-height (px @out-height)
                                :size @size :ed-out-order @ed-out-order}))
                 (reset! show? false))
        cancelfn (fn[]
                   (go (async/>! (hmi/get-adb [:main :chans :com]) :cancel))
                   (reset! show? false))]

    (fn [show?]
      (if (= curtid :xvgl)
        (alert-panel "Cannot edit <-> tab" cancelfn)
        [modal-panel
         :backdrop-color   "grey"
         :backdrop-opacity 0.4
         :child [v-box
                 :gap "10px"
                 :children
                 [[h-box
                   :gap "10px"
                   :children
                   [(when @rgap
                      [h-box :gap "10px"
                       :children
                       [[v-box :gap "15px"
                         :children
                         [[label :style {:font-size "18px"} :label "Ordering"]
                          [radio-button
                           :label "Row Ordered"
                           :value :row
                           :model order
                           :label-style (when (= :row @order)
                                          {:font-weight "bold"})
                           :on-change #(do (reset! size "auto")
                                           (reset! order %))]
                          [radio-button
                           :label "Column Ordered"
                           :value :col
                           :model order
                           :label-style (when (= :col @order)
                                          {:font-weight "bold"})
                           :on-change #(do (reset! size "none")
                                           (reset! order %))]
                          [h-box :gap "10px"
                           :children [[input-text
                                       :model eltsper
                                       :width "40px" :height "20px"
                                       :on-change #(reset! eltsper %)]
                                      [label :label (str "Elts/"
                                                         (if (= @order :row)
                                                           "row" "col"))]]]]]
                        [v-box :gap "10px"
                         :children
                         [[label :style {:font-size "18px"} :label "Gapping"]
                          [input-area "Row Gap" rgap]
                          [input-area "Col Gap" cgap]
                          [input-area "Flex size" size]]]
                        [v-box :gap "10px"
                         :children
                         [[label
                           :style {:font-size "18px"}
                           :label "Editor / Output"]
                          [radio-button
                           :label "First-Last"
                           :value :first-last
                           :model ed-out-order
                           :label-style (when (= :first-last @ed-out-order)
                                          {:font-weight "bold"})
                           :on-change #(reset! ed-out-order %)]
                          [radio-button
                           :label "Last-First"
                           :value :last-first
                           :model ed-out-order
                           :label-style (when (= :last-first @ed-out-order)
                                          {:font-weight "bold"})
                           :on-change #(reset! ed-out-order %)]]]]])

                    [v-box :gap "10px"
                     :children
                     [[input-area "Editor Width" width]
                      [input-area "Editor Height" height]
                      [input-area "Output Width" out-width]
                      [input-area "Output Height" out-height]]]]]

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
                  [ok-cancel donefn cancelfn]]]]))))



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
                                js/vegaLite.compile .-spec
                                (js->clj :keywordize-keys true)
                                (assoc :usermeta {:opts {:mode "vega"}})
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
                             (assoc
                              (js->clj (js/JSON.parse @input)
                                       :keywordize-keys true)
                              :usermeta {:opts {:mode "vega-lite"}})))
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
                   :width "730px"
                   :child [code-mirror
                           input {:name "javascript", :json true}
                           :tid :xvgl]]
         :panel-2 [box :size "auto"
                   :child [code-mirror
                           output "clojure"
                           :tid :xvgl]]
         :size    "auto", :width "2100px", :height "800px"]]])))
