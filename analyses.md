---
title: Analyses
layout: default
---

Over the past three years, Jepsen has analyzed over a dozen databases,
coordination services, and queues--and we've found replica divergence, data
loss, stale reads, lock conflicts, and much more. Here's every analysis we've
published:

|---|---:|
| Aerospike     | [3.5.4](https://aphyr.com/posts/324-call-me-maybe-aerospike) |
| Cassandra     | [2.0.0](https://aphyr.com/posts/294-call-me-maybe-cassandra) |
| Chronos       | [2.4.0](https://aphyr.com/posts/326-call-me-maybe-chronos) |
| Crate         | [0.54.9](https://aphyr.com/posts/332-jepsen-crate-0-54-9-version-divergence) |
| Elasticsearch | [1.1.0](https://aphyr.com/posts/317-call-me-maybe-elasticsearch), [1.5.0](https://aphyr.com/posts/323-call-me-maybe-elasticsearch-1-5-0) |
| etcd          | [0.4.1](https://aphyr.com/posts/316-call-me-maybe-etcd-and-consul) |
| Kafka         | [0.8 beta](https://aphyr.com/posts/293-call-me-maybe-kafka) |
| MariaDB Galera | [10.0](https://aphyr.com/posts/327-call-me-maybe-mariadb-galera-cluster) |
| MongoDB       | [2.4.3](https://aphyr.com/posts/284-call-me-maybe-mongodb), [2.6.7](https://aphyr.com/posts/322-call-me-maybe-mongodb-stale-reads) |
| NuoDB         | [1.2](https://aphyr.com/posts/292-call-me-maybe-nuodb) |
| Percona XtraDB Cluster | [5.6.25](https://aphyr.com/posts/328-call-me-maybe-percona-xtradb-cluster) |
| RabbitMQ      | [3.3.0](https://aphyr.com/posts/315-call-me-maybe-rabbitmq) |
| Redis         | [2.6.13](https://aphyr.com/posts/283-call-me-maybe-redis), [experimental WAIT](https://aphyr.com/posts/307-call-me-maybe-redis-redux) |
| RethinkDB     | [2.1.5](https://aphyr.com/posts/329-jepsen-rethinkdb-2-1-5), [2.2.3](https://aphyr.com/posts/330-jepsen-rethinkdb-2-2-3-reconfiguration) |
| Riak          | [1.2.1](https://aphyr.com/posts/285-call-me-maybe-riak) |
| VoltDB        | [6.3](https://aphyr.com/posts/331-jepsen-voltdb-6-3) |
| Zookeeper     | [3.4.5](https://aphyr.com/posts/291-call-me-maybe-zookeeper) |

## Get Tested

Would you like Jepsen to analyze your distributed system? Contact <a
href="aphyr@jepsen.io">aphyr@jepsen.io</a> for pricing.

Analyses generally take one to four months, depending on scope, and are bound
by our [research ethics policy](/ethics.html). We work with your team to
understand the system's guarantees, design a test for the properties you care
about, and build a reproducible test harness. We then help you understand what
any observed consistency anomalies mean, and work with your engineers to file
bugs and develop fixes.

Jepsen can also provide [assistance](/consulting.html) with your existing
Jepsen tests--developing new features, performance improvements,
visualizations, and more.

## Techniques

Jepsen occupies a particular niche of the correctness testing landscape. We
emphasize:

- Black-box systems testing: we evaluate real binaries running on real
  clusters. This allows us to test systems without access to their source, and
  without requiring deep packet inspection, formal annotations, etc. Bugs
  reproduced in Jepsen are *observable in production*, not theoretical.
  However, we sacrifice some of the strengths of formal methods: tests are
  nondeterministic, and we cannot prove correctness, only find errors.

- Testing *under distributed systems failure modes*: faulty networks,
  unsynchronized clocks, and partial failure. Many test suites only evaluate
  the behavior of healthy clusters, but production systems experience
  pathological failure modes. Jepsen shows behavior under strain.

- Generative testing: we construct random operations, apply them to the system,
  and construct a concurrent history of their results. That history is checked
  against a *model* to establish its correctness. Generative (or property-based)
  tests often reveal edge cases with subtle combinations of inputs.
