# [rnd.farm](http://rnd.farm)

A stream of human generated randomness

## why?

The topic of the upcoming issue #2 of
[Holo Magazine](http://holo-magazine.com/2/) is "**True Randomness**"
and we've been working on a Genetic Programming system to help design
the cover and other aspects. The
[Genetic Programming](https://en.wikipedia.org/wiki/Genetic_programming)
(GP) approach relies on selectively breeding programs by randomly
generating, mutating and mating a population of hundreds of deeply
nested
[abstract syntax trees](https://en.wikipedia.org/wiki/Abstract_syntax_tree)
over a potentially large number of generations. As you can guess, this
all requires an endless amount of random numbers...

Even though we could (and so far did) use one of the many readily
available pseudo-random number generators, Holo is also very much
about documenting & involving a community which takes pride in
experimenting with seeing things from alternative perspectives.

So in light of this:

We'd like to do a little experiment and see if a large (or not so
large) group of people can collectively generate better quality random
numbers than an algorithm. One would hope so, but I fear our sample
set will be full of bias. Yet even that will be fascinating to see if
and how this might impact an evolutionary process as it consumes our
random choices. At some point (once a sufficient amount of numbers has
been collected/farmed - with your incredible help! :) it should be
quite enlightening to analyze the distribution itself in more detail.

## how?

The initial site is merely targeted to the collection of **our**
random numbers (you can submit as many as you like), though a simple,
public read-only API with optional seeding will be implemented in the
next few days to help integrating this stream into the above
mentioned GP setup.

The range of numbers supported is 0 .. 2^62
(4,611,686,018,427,387,904) and that limit is being enforced.

The app was built with:

* Clojure
* Leiningen
* Ring
* Compojure
* Hiccup
* Environ

## Running locally (disconnected)

To start a local version on your own machine:

```bash
git clone https://github.com/postspectacular/rnd.farm.git
cd rnd.farm
RND_STREAM=random.txt lein ring server
```

After a few seconds, this will automatically open the site in your browser.
Numbers will be read & written to the file given as `RND_STREAM`

## License

Distributed under the [Apache Software License 2.0](http://www.apache.org/licenses/LICENSE-2.0).

Copyright © 2015 Karsten Schmidt
