(ns  aerial.saite.core
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

   [aerial.saite.codemirror
    :as cm
    :refer [code-mirror cm]]
   [aerial.saite.tabs
    :refer [editor-repl-tab interactive-doc-tab add-interactive-tab
            extns-xref
            alert-panel file-modal editor-box tab-box help-box tab<->]]
   [aerial.saite.savrest
    :as sr
    :refer [update-ddb get-ddb get-tab-data xform-tab-data load-doc]]

   [cljsjs.mathjax]

   [com.rpl.specter :as sp]

   [reagent.core :as rgt]
   [reagent.dom :as rgtd]

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




;;; Header ================================================================ ;;;


(defn saite-header []
  (let[alert? (rgt/atom false)
       _ (update-ddb [:alert :show?] alert? [:alert :txt] "")
       close-alert (fn [event] (reset! alert? false))
       show? (rgt/atom false)
       session-name (rgt/atom "")
       file-name (rgt/atom "")
       url (rgt/atom "")
       all (rgt/atom {:all? false :files []})
       charts (rgt/atom false)
       docchoices (rgt/atom nil)
       codechoices (rgt/atom nil)
       mode (rgt/atom nil)
       donefn (fn[event]
                (go (async/>! (hmi/get-adb [:main :chans :com])
                              {:session @session-name :file @file-name
                               :url @url :charts @charts :all @all}))
                (reset! show? false))
       cancelfn (fn[event]
                  (go (async/>! (hmi/get-adb [:main :chans :com]) :cancel))
                  (reset! show? false))]
    (update-ddb [:main :throbber] (rgt/atom false))
    (fn []
      [h-box :justify :between
       :children
       [[h-box :gap "10px" :max-height "30px"
         :children
         [[gap :size "5px"]
          [box :child [:img {:src (hmi/get-adb [:main :logo])}]]
          [gap :size "5px"]
          [title
           :level :level3
           :label [:span.bold @session-name]]

          (if (deref (get-ddb [:main :throbber]))
            [cm/spinner]
            [gap :size "30px"])

          [border :padding "2px" :radius "2px"
           :l-border "1px solid lightgrey"
           :r-border "1px solid lightgrey"
           :b-border "1px solid lightgrey"
           :child
           [h-box :gap "10px"
            :children
            [[md-circle-icon-button
              :md-icon-name "zmdi-upload" :size :smaller
              :tooltip "Upload Document"
              :on-click
              #(go (let [ch (hmi/get-adb [:main :chans :com])]
                     (js/console.log "upload clicked")
                     (reset! session-name (get-ddb [:main :docs :dir]))
                     (reset! file-name (get-ddb [:main :docs :load]))
                     (reset! url nil)
                     (reset! mode :load)
                     (reset! show? :doc)
                     (let [location (async/<! ch)]
                       (when (not= :cancel location)
                         (let [fname (location :file)
                               dname (location :session)
                               location (assoc
                                         location
                                         :file (str fname ".clj"))]
                           (update-ddb [:main :docs :load] fname
                                       [:main :docs :save] fname
                                       [:main :docs :dir] dname
                                       [:main :docs :doc-loaded?] true)
                           (when (not= @session-name
                                       (hmi/get-adb [:main :uid :name]))
                             (hmi/set-session-name @session-name))
                           (hmi/send-msg
                            {:op :load-doc
                             :data {:uid (hmi/get-adb [:main :uid])
                                    :location location}}))))))]

             [md-circle-icon-button
              :md-icon-name "zmdi-download" :size :smaller
              :tooltip "Save Document"
              :on-click
              #(go (let [ch (hmi/get-adb [:main :chans :com])]
                     (js/console.log "download clicked")
                     (reset! session-name (get-ddb [:main :docs :dir]))
                     (reset! file-name (get-ddb [:main :docs :save]))
                     (reset! charts false)
                     (reset! mode :save)
                     (reset! show? :doc)
                     (let [location (async/<! ch)]
                       (when (not= :cancel location)
                         (cond (location :charts)
                               (sr/gen-chart-zip)

                               (not (get-ddb [:main :docs :doc-loaded?]))
                               (do (update-ddb [:alert :txt]
                                               "*** NO DOCUMENT LOADED")
                                   (reset! (get-ddb [:alert :show?]) true))

                               :else
                               (let [fname (location :file)
                                     dname (location :session)
                                     location (assoc
                                               location
                                               :file (str fname ".clj"))]
                                 (update-ddb [:main :docs :save] fname
                                             [:main :docs :dir] dname)
                                 (let [spec-info (xform-tab-data
                                                  (get-tab-data))]
                                   (hmi/send-msg
                                    {:op :save-doc
                                     :data {:loc location
                                            :info spec-info}}))))))))]

             [md-circle-icon-button
              :md-icon-name "zmdi-format-valign-top" :size :smaller
              :tooltip "Load Code"
              :on-click
              #(go (let [ch (hmi/get-adb [:main :chans :com])
                         curtabid (hmi/get-cur-tab :id)
                         [tdir tfile] (get-ddb [:tabs :extns curtabid :file])]
                     (js/console.log "Insert code clicked")
                     (reset! session-name (or tdir
                                              (get-ddb [:main :files :dir])))
                     (reset! file-name (or tfile
                                           (get-ddb [:main :files :save])))
                     (reset! url nil)
                     (reset! mode :getcode)
                     (reset! show? :code)
                     (let [location (async/<! ch)]
                       (when (not= :cancel location)
                         (let [fname (location :file)
                               dname (location :session)]
                           (update-ddb [:main :files :save] fname
                                       [:main :files :dir] dname)
                           #_(printchan location)
                           (hmi/send-msg
                            {:op :get-code
                             :data {:uid (hmi/get-adb [:main :uid])
                                    :location location}}))))))]

             [md-circle-icon-button
              :md-icon-name "zmdi-format-valign-bottom" :size :smaller
              :tooltip "Save Code"
              :on-click
              #(go (let [ch (hmi/get-adb [:main :chans :com])
                         curtabid (hmi/get-cur-tab :id)
                         [tdir tfile] (get-ddb [:tabs :extns curtabid :file])]
                     (js/console.log "Save code clicked")
                     (reset! session-name (or tdir
                                              (get-ddb [:main :files :dir])))
                     (reset! file-name (or tfile
                                           (get-ddb [:main :files :save])))
                     (reset! all {:all? false :files []})
                     (reset! mode :savecode)
                     (reset! show? :code)
                     (let [location (async/<! ch)]
                       (when (not= :cancel location)
                         (if (get-in location [:all :all?])
                           (when (seq (get-in location [:all :files]))
                             (let [all-files (get-in location [:all :files])]
                               (doseq [[tid [d f]] all-files]
                                 (when (cm/editor-active? tid)
                                   (let [file-map {:session d :file f}]
                                     (hmi/send-msg
                                      {:op :save-code
                                       :data {:location file-map
                                              :code (cm/get-ed-src tid)}}))))))
                           (let [fname (location :file)
                                 dname (location :session)]
                             (update-ddb [:main :files :save] fname
                                         [:main :files :dir] dname
                                         [:tabs :extns curtabid :file] [dname
                                                                        fname])
                             #_(printchan location)
                             (hmi/send-msg
                              {:op :save-code
                               :data {:location location
                                      :code (cm/get-cur-src)}})))))))]

             (when @show?
               (when (nil? @docchoices)
                 (reset! docchoices ((get-ddb [:main :docs]) :choices)))
               (when (nil? @codechoices)
                 (reset! codechoices ((get-ddb [:main :files]) :choices)))
               (if (= @show? :doc)
                 [file-modal docchoices session-name file-name mode
                  url charts all
                  donefn cancelfn]
                 [file-modal codechoices session-name file-name mode
                  url charts all
                  donefn cancelfn]))] ]]

          [gap :size "20px"]
          [editor-box]

          [gap :size "20px"]
          [tab-box]

          (when @alert?
            [alert-panel (get-ddb [:alert :txt]) close-alert])]]

        [help-box]]])))




;;; Messaging ============================================================ ;;;


(defmethod user-msg :error [msg]
  (let [alert?  (get-ddb [:alert :show?])
        errinfo (msg :data)
        eval?   (errinfo :eval)
        errname (-> errinfo :error (cljstr/split #"\.") last)
        errmsg (if (errinfo :cause) (errinfo :cause) (errinfo :msg))
        errtxt  (str "ERROR : " errname  ", " errmsg)]

    (when eval?
      (let [chankey (errinfo :chankey)
            ch (get-ddb [:main :chans chankey])]
        (go (async/>! ch {:error errtxt}))))

    (update-ddb [:alert :txt] errtxt)
    (reset! alert? true)))



(def newdoc-data (atom []))

(defmethod user-msg :load-doc [msg]
  (let [data (msg :data)]
    (reset! newdoc-data data)
    (load-doc data extns-xref)
    (when (get-ddb [:main :evalcode :on-load])
      (cm/eval-code-after-load))))

(defmethod user-msg :get-code [msg]
  (let [code (msg :data)]
    (cm/insert-src-cur code)))


(defmethod user-msg :evalres [msg]
  (let [res (msg :data)
        chankey (res :chankey)
        ch (get-ddb [:main :chans chankey])]
    #_(printchan msg chankey)
    (go (async/>! ch res))))


(defmethod user-msg :clj-read [msg]
  (let [data (msg :data)
        render? (data :render?)
        clj (dissoc data :render?)
        result (if render?
                 clj
                 (try (-> clj clj->js (js/JSON.stringify nil, 2))
                      (catch js/Error e (str e))))
        ch (hmi/get-adb [:main :chans :convert])]
    #_(printchan render? result)
    (go (async/>! ch result))))



(defn default-start-tab []
  (let [px #(str % "px")
        defaults (get-ddb [:main :interactive-tab])
        ed-defaults (get-ddb [:main :editor])
        edsize (get-in ed-defaults [:size :edout])
        info {:edtype :editor :ns (symbol (defaults :nssym "doc.code"))
              :id :scratch :label "Scratch"
              :width (px (edsize :width "730"))
              :height (px (edsize :height "850"))
              :out-width  (px (edsize :out-width "730"))
              :out-height (px (edsize :out-height "850"))
              :layout :left-right :ed-out-order :first-last}]
    (add-interactive-tab info)))


(defn xform-tab-defaults [defaults]
  (->> defaults
       (mapv (fn[[k v]] [k (if (number? v) (str v) v)]))
       (into {})))

(defmethod user-msg :app-init [msg]
  (let [{:keys [save-info editor interactive-tab
                locs evalcode doc]} (msg :data)
        {:keys [docs chart code data downloads]} locs
        docchoices (into {} (save-info :docs))
        docdirs (-> docchoices keys sort)
        codechoices (into {} (save-info :code))
        codedirs (-> codechoices keys sort)
        interactive-tab (xform-tab-defaults interactive-tab)
        {:keys [name mode theme size key-bindings]} editor
        theme (if theme theme "zenburn")
        size {:edout (xform-tab-defaults (size :edout))
              :eddoc (xform-tab-defaults (size :eddoc))}
        key-bindings (cm/xform-kb-syms key-bindings)
        editor (assoc editor
                      :theme theme :size size :key-bindings key-bindings)]
    #_(printchan :APP-INIT save-info editor)
    #_(printchan :DOCCHOICES docchoices :DIRS docdirs)

    (update-adb [:main :chans :convert] (async/chan)
                [:main :chans :com] (async/chan)
                [:main :chans :data] (async/chan))

    (update-ddb [:main :files :choices] codechoices
                [:main :files :dirs] codedirs
                [:main :files :dir]  (first codedirs)
                [:main :files :save] (-> codedirs first codechoices sort first)
                [:main :files :load] (-> codedirs first codechoices sort first)

                [:main :docs :choices] docchoices
                [:main :docs :dirs] docdirs
                [:main :docs :dir]  (first docdirs)
                [:main :docs :save] (-> docdirs first docchoices sort first)
                [:main :docs :load] (-> docdirs first docchoices sort first)
                [:main :docs :doc-loaded?] false

                [:main :locs] locs
                [:main :evalcode] evalcode
                [:main :editor] editor
                [:main :interactive-tab] interactive-tab
                [:main :throbber] (rgt/atom false)
                [:main :doc] doc

                [:editors] {}
                [:main :chans] {}
                [:tabs :extns :$split] 29.0)

    (update-ddb [:tabs :extns :xvgl]
                {:width "0px" :height "0px"
                 :out-width "0px" :out-height "0px"})
    (add-tab {:id :xvgl
              :label "<->"
              :opts {:extfn (tab<-> :NA)}})
    (default-start-tab)))


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
                  tid (-> usermeta :tab :id)
                  spec (dissoc (get-vspec tid vid) :data)]
              (assoc-in spec [:data :values] data)))
          data-maps))


(defmethod user-msg :data [msg]
  (let [{:keys [chankey data]} (msg :data)
        ch (get-ddb [:main :chans chankey])]
    (printchan :DATA chankey)
    (go (async/>! ch data))))




;;; Cross namespace helpers =============================================== ;;;

;;; These are functions that support end user interaction with certain
;;; user level coding requirements from capabilities in other
;;; aerial.saite.* namespaces.  This is simpler and more robust than
;;; trying to require all these into user level namespaces


(defn read-data [path]
  (js/Promise.
   (fn[resolve reject]
     (let [ch (async/chan)
           chankey (keyword (gensym "chan-"))
           data (volatile! nil)
           tid (hmi/get-cur-tab :id)
           eid (get-ddb [:tabs :extns tid :eid])
           throbber (get-ddb [:editors tid eid :opts :throbber])]
       (update-ddb [:main :chans chankey] ch)
       (hmi/send-msg {:op :read-data
                      :data {:uid (hmi/get-adb [:main :uid])
                             :chankey chankey
                             :path path
                             :from :file}})
       (reset! throbber true)
       (go (vreset! data (async/<! ch))
           (reset! throbber false)
           (update-ddb [:main :chans chankey] :rm)
           (resolve @data))))))


(defn selfhost-jvm-eval [code]
  (let [tid (hmi/get-cur-tab :id)
        nssym (get-ddb [:tabs :extns tid :ns])
        eid (get-ddb [:tabs :extns tid :eid])]
    (cm/selfhost-eval-on-jvm nssym code tid eid)))


(defn get-cur-cm []
  (let [tid (hmi/get-cur-tab :id)
        eid (get-ddb [:tabs :extns tid :eid])]
    (get-ddb [:tabs :cms tid eid :$ed])))

(defn get-cm-cb []
  (let [cm (get-cur-cm)] cm.CB))


(defn get-current-cm-frame-info []
  (let [cm (get-cur-cm)]
    (cm/current-cm-frame-info cm)))

(defn add-update-frame
  "Programmatically add, or update if exists, the picture frame with
  frame id `fid` with the new content given by the legal fully
  transformed template `picframe` defining the frame and any
  visualization.  `locid` is the relative placement location, one of
  `:beg` for beginning of doc body
  `:end` for end of doc body
  `:before` for before the current frame
  `:after` for after the current frame"
  [picframe fid locid]
  (let [cm (get-cur-cm)
        cb cm.CB
        tid (hmi/get-cur-tab :id)
        tabfid (some #(= fid %) (cm/get-tab-frame-ids tid))
        pos (if (= locid :beg) :same :after)]
    (if (not tabfid)
      (aerial.saite.tabops/add-frame picframe locid pos)
      (aerial.saite.tabops/update-frame :frame picframe))))

(defn delete-frame
  "Programmatically delete the frame with frame id `fid` from the
  current tab's document body."
  [fid]
  (let [tid (hmi/get-cur-tab :id)
        tabfid (some #(= fid %) (cm/get-tab-frame-ids tid))]
    (when tabfid
      (aerial.saite.tabops/remove-frame fid))))



(defn run-prom-chain
  "Run the promise chain implicit in promise `prom` - if `prom` results
  in another promise, recurse on this result until the result is no
  longer a promise.  At this end of the chain, run the function
  `bodyfn` in the context of the chain's results"
  [prom bodyfn]
  (if (instance? js/Promise prom)
    (.then prom (fn[res] (run-prom-chain res bodyfn)))
    (bodyfn prom)))


(defn get-cur-date
  "Get the current date as a string 'y-m-d'.  The month m is either
  1-12 or, if `:month-name` is given and true, the 3 letter
  abbreviation for month, eg, 'Jan', 'Mar', 'Jul'."
  [& {:keys [month-name]}]
  (let [int-mnth2name-mnth {1 "Jan", 2 "Feb", 3 "Mar", 4 "Apr"
                            5 "May", 6 "Jun", 7 "Jul", 8 "Aug"
                            9 "Sep", 10 "Oct", 11 "Nov", 12 "Dec"}
        date (js.Date.)
        y (.getFullYear date)
        m (inc (.getMonth date))
        m (if month-name (int-mnth2name-mnth m) m)
        d (.getDate date)]
    (cljstr/join "-" [y m d])))




;;; Call Backs ============================================================ ;;;

#_(js/MathJax.Hub.Queue #js ["Typeset" js/MathJax.Hub])
#_(js/MathJax.Hub.Queue
   #js ["Typeset" js/MathJax.Hub
        (js/document.getElementById (get-ddb [:mathjax]))])

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
  ([spec frame] #_(printchan :FRAME-CALLBACK :spec spec)
   (let [fid (frame :frameid)]
     (go (async/>! mathjax-chan (name fid)))
     [spec frame])))




(defn set-md-defaults
  "Set the tab defaults for various markdown and codemirror document
  parameters.`optsmap` is a nested map with two top level keys: `:md`
  and `:cm` for general markdown parameters and parameters specific to
  the layout and behavior of codemirror elements respectively.

  `:md` a map of the following keys and associated values:
  `:vmargin` <a string giving CSS size - typically 'xxxpx'>
  The vertical spacing of a markdown element from others
  `:margin` <a string giving a CSS size - typically 'xxxpx'>
  The horizontal spacing of a markdown element from others
  `:width` <a string giving a CSS size - typically 'xxxpx'>
  The width of the textual field for a markdown element
  `:font-size` <a string giving a CSS size - typically 'xxxpx'>
  Size of the font for the markdown element text (including LaTex)

  `:cm` a map of the following keys and associated values:
  `:width` <a string giving a size in pixels 'xxxpx'>
  The width of the editor pane
  `:height` <a string giving a size in pixels 'xxxpx'>
  The height of the editor pane
  `:out-width` <a string giving a size in pixels 'xxxpx'>
  The width of the associated output pane if editor is live
  `:out-height` <a string giving a size in pixels 'xxxpx'>
  The height of the associated output pane if editor is live
  `:readonly` <boolean true or false>
  Whether the editor is live (editable) or a static pane
  If `:readonly` is true editor is a static pane w/o an output pane
  `:layout` <:left-right or :up-down>
  Orientation (relative placement) of the editor and its output pane.
  `:ed-out-order` <:first-last or :last-first>
  The order of the editor and its output wrt the layout. If :first-last
  the editor matches 'left' or 'up' and output pane matches 'right' or
  'down'.  Vice-versa for :last-first.
  "
  [optsmap]
  (cm/set-md-defaults optsmap))


(defn calc-dimensions [tid opts]
  (let [pxs-ch 9.125 ; (/ 730 90)
        pxs-row 20   ; experimentation
        tabdefs (cm/get-md-defaults :cm)
        src (opts :src "")
        lines (cljstr/split-lines src)
        lcnt (count lines)
        lsiz (->> lines (mapv count) (sort >) first)
        width-pxs (-> pxs-ch (* lsiz) Math/ceil (str "px"))
        height-pxs (-> pxs-row (* lcnt) (+ 10) Math/ceil (str "px"))
        width (opts :width (tabdefs :width width-pxs))
        height (opts :height (tabdefs :height height-pxs))
        out-width (opts :out-width
                        (tabdefs :out-width
                                 (if (not (opts :readonly)) width width-pxs)))
        out-height (opts :out-height
                         (tabdefs :out-height
                                  (if (opts :readonly true) "0px" "50px")))]
    [width height out-width out-height]))

(defn mdcm [& opts]
  (fn [& opts]
    (let [optsmap (->> opts (partition-all 2) (mapv vec) (into {}))
          cmfn (cm)
          tinfo (hmi/get-cur-tab)
          tid (tinfo :id)
          vid (-> :specs tinfo first :usermeta :vid)
          eid (or (optsmap :id)
                  (-> "te-" (str (Math/floor (/ (.now js/Date) 10)))))
          fid (optsmap :fid)
          curtab-uinfo (get-ddb [:tabs :extns tid])
          [width height out-width out-height] (calc-dimensions tid optsmap)
          layout (optsmap :layout :up-down)
          ed-out-order (optsmap :ed-out-order :first-last)
          readonly (if (optsmap :readonly true) "nocursor" false)
          src (optsmap :src "")]
      #_(printchan :MDCM :FID fid :ED-OUT-ORDER ed-out-order :LAYOUT layout)
      [cmfn :id eid :tid tid :vid vid :fid fid
       :width width :height height
       :out-width out-width :out-height out-height
       :layout layout :ed-out-order ed-out-order
       :readonly readonly :tbody :true
       :src src])))




(defn add-ratom
  "Add a ratom for a dashboard model. `id` is the key for the ratom in
  the dashboard model db and is typically a keyword, but must be a
  print readably object. `val` is the initial value for the
  model. Dashboard ratoms are used in conjunction with 'symbol
  translated' callback functions (see `add-symxlate`) to hold the
  state of reactive components in dashboards."
  [id val]
  (sr/add-ratom id val))

(defn get-ratom
  "Return the ratom associated with `id` in the dashboard model db.  See
  `add-ratom` for more details."
  [id]
  (sr/get-ratom id))



(defn add-symxlate
  "Add a symbol to function mapping to the symbol to function
  translation db. 'sym' must be a symbol.  Typically it will be a
  symbol in the namespace of the tab issuing the call.  'val' must be
  a function. The symbol may then be used in place of the function in
  any associated hiccup and/or re-com components which require a
  function at the given location.  The primary purpose is to enable
  saving and restoring reactive function parameters across document
  loads and server restarts.

  Example:

  ;;; Set up the symbol translation for a slider component
  (let [sval (sc/add-ratom :m4 \"1.0\")
        chgfn #(density-fn % :rhist sval)]
    (sc/add-symxlate 'density-fn chgfn))

  ...

  [slider
    :model (sc/get-ratom :m4 (str begden))
    :min 0.2, :max 2.0, :step 0.2
    :width \"200px\"
    :on-change 'density-fn]"
  [sym val]
  (sr/add-symxlate sym val))

(defn get-symxlate
  "Return the function associated with the symbol `sym` in the symbol to
  function translation db.  See `add-symxlate` for more details."
  [sym]
  (sr/get-symxlate sym))

(defn symxlate-callback [sym]
  (let [snm (name sym)]
    (cond
      (= snm "cm") (mdcm)
      :else (get-symxlate sym))))




;;; Instrumentors ========================================================= ;;;

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




;;; Startup ============================================================== ;;;

;;; Load cache...
(cljs.js/load-analysis-cache!
 aerial.saite.compiler/state 'aerial.saite.core
 (aerial.saite.analyzer/analyzer-state 'aerial.saite.core))


(when-let [elem (js/document.querySelector "#app")]
  (hc/update-defaults
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
   :TID #(hmi/get-cur-tab :id)
   :TLBL #(-> (let [tid (% :TID)] (if (fn? tid) (tid) tid))
              name cljstr/capitalize)
   :OPTS (hc/default-opts :vgl), :TOPTS (hc/default-opts :tab))
  (start :elem elem
         :port js/location.port
         :host js/location.hostname
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
                        :fid :FID :at :AT :pos :POS}
                :opts :OPTS
                :vid :VID,
                :msgop :MSGOP,
                :session-name :SESSION-NAME}
     :AT :end :POS :after
     :MSGOP :tabs, :SESSION-NAME "Exploring"
     :TID #(hmi/get-cur-tab :id)
     :TLBL #(-> (let [tid (% :TID)] (if (fn? tid) (tid) tid))
                name cljstr/capitalize)
     :OPTS (hc/default-opts :vgl), :TOPTS (hc/default-opts :tab))
    (start :elem elem
           :port 3000
           :symxlate-cb symxlate-callback
           :frame-cb frame-callback
           :header-fn saite-header
           :instrumentor-fn instrumentor))






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



  (let[frame (js/document.getElementById "f1")]
    (js/console.log (aget frame.childNodes 2)))

  (let[frame (js/document.getElementById "f1")
       bottom (aget frame.childNodes 4)]
    (js/console.log bottom.childNodes))

  (let [children `[h-box [md "### title"] [p "some text"]]]
    (js/console.log (rgt/as-element children)))

  (let [children `[h-box [md "### title"] [p "some text"]]
        div (js/document.createElement "div")]
    (rgtd/render children div)
    (js/console.log (aget div.childNodes 0)))




  (def loaded? (r/atom false))

  (.addEventListener js/document "load" #(reset! loaded? true))

  (defn app []
    [:div {:class (when @loaded? "class-name")}])


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
