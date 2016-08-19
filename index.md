---
title: Jepsen
layout: default
---

<iframe width="520" height="293" src="http://www.ustream.tv/embed/recorded/61443262?html5ui" allowfullscreen webkitallowfullscreen scrolling="no" frameborder="0" style="border: 0 none transparent;"></iframe>

## Distributed Systems Safety Analysis

Jepsen is an effort to improve the safety of distributed databases, queues,
consensus systems, etc. We maintain an open source [software
library](https://github.com/aphyr/jepsen) for systems testing, as well as [blog
posts](https://aphyr.com/tags/jepsen) and [conference
talks](http://www.ustream.tv/recorded/61443262) exploring particular systems'
failure modes. In each analysis we explore whether the system lives up to its
documentation's claims, file new bugs, and suggest recommendations for
operators.

Jepsen pushes vendors to make accurate claims and test their software
rigorously, helps users choose databases and queues that fit their needs, and
teaches engineers how to evaluate distributed systems correctness for
themselves.

## Recent work

- Research for Crate.io led to cases of [dirty reads, replica divergence, and
  lost updates](https://github.com/elastic/elasticsearch/issues/20031) in
  Elasticsearch.

- Jepsen found that document versions in Crate.io [do not uniquely identify](https://aphyr.com/posts/332-jepsen-crate-0-54-9-version-divergence)
  a particular version of a document, allowing lost updates.

- We worked with VoltDB to [discover and
  fix](https://aphyr.com/posts/331-jepsen-voltdb-6-3) stale and dirty reads in
  their SQL database, and, in uncommon configurations, two bugs leading to the
  loss of acknowledged updates.

- Jepsen helped RethinkDB [identify and
  resolve](https://aphyr.com/posts/330-jepsen-rethinkdb-2-2-3-reconfiguration)
  a bug that led to stale reads, lost updates, and table failure during cluster
  reconfiguration.

In addition to [public analyses](/analyses.html), Jepsen offers tech talks,
[training classes](/training.html), and [distributed systems
consulting](/consulting.html) services.
