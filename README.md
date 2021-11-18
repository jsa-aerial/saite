[![Clojars Project](https://img.shields.io/clojars/v/aerial.saite.svg)](https://clojars.org/aerial.saite)

# saite
Data exploration and interactive documents; with editor support (emacs, vim, sublime, and paredit), graphics and visualization; markdown; and LaTex. 咲いて (in bloom). Built on top of [Hanami](https://github.com/jsa-aerial/hanami) Vega/Vega-Lite library, [CodeMiror](https://codemirror.net), [MathJax](https://www.mathjax.org), amd [Specter](https://www.mathjax.org).

<a href="https://jsa-aerial.github.io/aerial.saite/index.html"><img src="https://github.com/jsa-aerial/saite/blob/master/resources/public/images/in-bloom.png" align="left" hspace="10" vspace="6" alt="saite logo" width="150px"></a>

**Saite** is a Clojure(Script) client/server application for the dynamic exploration of data and the creation and sharing of interactive documents and dashboards.  Documents fully support creation of interactive visualizations, coupled with editors, markdown and LaTex.  Dashboards can use any [Re-com](https://re-com.day8.com.au/#/introduction) component for interactive widgets mixed with Vega/Vega-Lite visualizations.  These documents and dashboards are saved as simple text and thus may be shared via any number of ways. End user creation of documents is highly declarative in form and nature.  Dashboards may use of mixture of client side widgets and visualization and server side dataset processing for the final data to use.


Table of Contents
=================

   * [Introduction](#introduction)
   * [Installation](#installation)
      * [Uberjar](#uberjar)
      * [Library](#library)
   * [Outline](#outline)
   * [Overview](#overview)

[toc](https://github.com/ekalinin/github-markdown-toc)
# Introduction


**Saite** is a Clojure(Script) client/server application for dynamic interactive data exploration, mixed server/client dashboard creation, and the creation of live shareable documents.  Documents and dashboards fully support creation of interactive visualizations, coupled with editors, [Re-com components](https://re-com.day8.com.au/#/introduction), markdown and LaTex.  These documents and dashboards are saved as simple text and thus may be shared via any number of ways. End user creation of documents is highly declarative in form and nature, while dashboards are a mix of declarative forms and reactive functions.

[Vega-Lite](https://vega.github.io/vega-lite/) (VGL) and/or [Vega](https://vega.github.io/vega/) (VG) specifications. These specifications are declarative and completely specified by _data_ (JSON maps). VGL compiles into the lower level grammar of VG which in turn compiles to a runtime format utilizting lower level runtime environments such as [D3](https://d3js.org/), HTML5 Canvas, and [WebGL](https://github.com/vega/vega-webgl-renderer).

Visualizations are formed from parameterized [templates](https://github.com/jsa-aerial/hanami#templates-substitution-keys-and-transformations) which are recursively transformed into legal VGL or VG specifications. In Saite, creating and transforming these templates is generally done on the server side in typical REPL style development. Transformed templates (with their data or data source) are sent to one or more sessions (brower viewers) for rendering.



Saite also functions as an example application built with [Hanami](https://github.com/jsa-aerial/hanami). As such it has all the capability of Hanami's _template_ system and recursive transformation of parameterized templates.

Saite also uses the [tab system](https://github.com/jsa-aerial/hanami#tabs) provided by Hanami for automatic tab construction and updates, plus the application specific tab capabilities of that system. Further, Saite makes use of Hanami's visualization [messaging system](https://github.com/jsa-aerial/hanami#messages), in particular, the `user-msg` multimethod with implementations for application initialization, namespace creation, server side code execution, saving and reading of documents and code files, saving all visualizations, et.al.


# Installation

## Uberjar

There is a self installing/updating uberjar that makes a plug and play application of Saite. This is the quickest and easiest way to simply *use* Saite as a kind of "super notebook" or Clojure(Script) analog of RStudio.  See [aerosaite](https://github.com/jsa-aerial/aerosaite) for more on this.


## Library

To install, add the following to your project `:dependencies`:

    [aerial.saite "1.0.0"]

# Outline

[total new documentation being written]

An attempt at an outline for the documentation

* Overview
  - Install
  - Update
  - Running
  - Videos
  - Tutorials

  - Capabilities
    - Dynamic data exploration
    - Interactive documents
    - Full client/server dashboards

  - startup
    - <-> convert tab
    - Scratch tab

  - Controls panel
    - Logo
    - Session marker
    - Application spinner
    - Load and save group
      - Documents
      - Code files
    - Editor visibility group
      - Open (show)
      - Close (hide)
    - Tab manipulation group
      - Add
      - Duplicate
      - Frame mgt
      - Undo and Redo
      - Left/right movement
      - Edit
      - Delete
    - Info group
      - Theme
      - Quick help
      - Documentation

  - Tabs panel

* Control panel

* Tabs
  - Editor / output
  - Editor / document body

* Editors
  - Modes
    - emacs, vim, sublime
  - Paredit
  - Keys
    - Bindings
    - Functions
    - Configuration
    - Defaults

* Namespaces
  - Per tab and/or cross tab
  - Dynamic dependencies
  - Requirements

* Document construction
  - Picture frames
  - Visualizations
  - Markdown
  - LaTex
  - Live editors
  - Tab local frame markdown and live editor defaults
  - Auto frame insertions
    - Vis
    - Text
    - Code mirror
    - Markdown

* Dashboard construction
  - Reactive components
  - Callback symbols and maps
  - Mixed client/server code

* Auto run on document load
  - Three modes
    - :clj, :cljs, :mixed
  - Order of running
  - Methods


# Overview


