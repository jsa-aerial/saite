# saite
Exploratory graphics and visualization system. 咲いて (in bloom). Built on top of hanami vega/vega-lite library

<a href="https://saite.github.io"><img src="https://github.com/jsa-aerial/saite/blob/master/resources/public/images/in-bloom.png" align="left" hspace="10" vspace="6" alt="saite logo" width="150px"></a>

**Saite** is a Clojure(Script) mini "client/server" application for exploratory creation of interactive visualizations based in [Vega-Lite](https://vega.github.io/vega-lite/) (VGL) and/or [Vega](https://vega.github.io/vega/) (VG) specifications. These specifications are declarative and completely specified by _data_ (JSON maps). VGL compiles into the lower level grammar of VG which in turn compiles to a runtime format utilizting lower level runtime environments such as [D3](https://d3js.org/), HTML5 Canvas, and [WebGL](https://github.com/vega/vega-webgl-renderer).

Typical work flow starts by requiring `aerial.saite.core` and running the `start` function which takes a port. This port is for the websocket messaging. Browsing to this port on localhost will open the viewer.

Visualizations are formed from parameterized templates (see [Hanami](https://github.com/jsa-aerial/hanami) which are recursively transformed into legal VGL or VG specifications. In Saite, creating and transforming these templates is done on the server side in typical REPL style development. Generally, transformed templates (with their data or data source) are sent to one or more sessions (brower viewers) for rendering.

Saite also functions as an example application built with [Hanami](https://github.com/jsa-aerial/hanami). As such it has all the capability of Hanami's _template_ system and recursive transformation of parameterized templates.

Saite also uses of the _tab system_ provided by Hanami for automatic tab constructtion and updates, plus the application specific tab capabilities of that system. Further, Saite makes use of Hanami's visualization messaging system, in particular, the `user-msg` multimethod with implementations for the `:app-init` user msg as well as the `:data` msg for streaming data plots/charts.

In addition, Saite also makes use of `default-header-fn` provided by Hanami. This creates a simple page header giving the 'session name' for a session as well as a input area to change the name of the session. Updating is based on session name - all sessions with the same name will get the updates from the server.


## Installation

To install, add the following to your project `:dependencies`:

    [aerial.saite "0.1.0"]


## Features

* Hanami's parameterized templates with recursive transformations
* Simple tab page structure
  * Uses Hanami's built in tabbing system
  * Each tab can have arbitrary number of _independent_ visulaizations
  * Tab layout is configurable - row/col and number of elements/(row/col)
  * Tabs can have names that make sense to you
* Initial tab for converting VGL to Clj and VGL to VG and rendered as Clj
  * useful for creating starting point specs that you turn into templates
  * useful for merging VGL with VG only capabilities
  * Visualization templates have a default tab, with name "Expl"
  * Any number of additional tabs can be created and used
* Multiple named based sessions
  * Each session can be given a name (the default is 'Exploring')
  * An input area is available to change the name
  * All sessions with the same name get updates for that name
  * Sessions with different _names_ are independent
* Data streaming
  * realtime charts
  * can work for multiple tabs/charts simultaneously
  * **NOTE** not operational in version 0.1.0


## Usage

```Clojure
(ns exploring1.examples
  (:require [aerial.saite.core :as as]
            [aerial.hanami.common :as hc]
            [aerial.hanami.templates :as ht]
            [aerial.hanami.core :as hmi]
            ...
            )
  ...)

(as/start 3000)
```

Browse to `localhost:3000` and you will see initial session page:

![Saite pic 1](resources/public/images/start-page.png?raw=true)