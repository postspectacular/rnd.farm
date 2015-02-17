# [rnd.farm](http://rnd.farm)

A stream of human generated randomness

## updates

### 2015-02-17 - v2 released with collection via websocket

A new version has been released, now collecting inherent randomness
from each user's mouse/touch movements and key presses and their
differences in timing frequency both in terms of interaction and
network latency. This provides us with magnitudes more of raw
collection data and less effort for you, the user. Additionally, all
submitted numbers are both stored as-is and secondly are run through a
chunking [SHA-256](http://en.wikipedia.org/wiki/SHA-2) cryptographic
hash function to distribute bits more uniformly. This is a similar
approach as used by tools like TrueCrypt or Keepass. More details
about this approach can be found at these links:

- [Gathering+Entropy+from+Mouse+Events](http://etutorials.org/Programming/secure+programming/Chapter+11.+Random+Numbers/11.21+Gathering+Entropy+from+Mouse+Events+on+Windows/)
- [Keepass](http://keepass.info/)
- [TrueCrypt](http://truecrypt.org)

Whilst connected to the site (and if your browser supports
WebSockets), you can see activity of all other current users. During
recording your own random events, a histogram of collected bytes is
displayed, giving an indication of the random distribution of your
submitted source data. Technical note: since this histogram is byte
based and submitted values have a variable bit length, the "00" bin
(grouping bytes between 0x00 - 0x0f) will likely be the most dominant.
This has no impact on the SHA-256 digest these bytes are fed through.
The original raw values are *not* split into bytes and stored as is.

## why?

"Chance, predictability, and (true) randomness" is the theme of the
'ideas' section of [Holo Magazine](http://holo-magazine.com/2/)'s
upcoming second issue and we've been working on a
[Genetic Programming](https://en.wikipedia.org/wiki/Genetic_programming)
system to create a design for the cover and other aspects. The GP
approach relies on selectively breeding programs by randomly
generating, mutating and mating a population of hundreds of deeply
nested
[abstract syntax trees](https://en.wikipedia.org/wiki/Abstract_syntax_tree)
over a potentially large number of generations. As you can guess, this
all requires an endless amount of random numbers...

Even though we could (and so far did) use one of the many
pseudo-random number generators readily available, Holo is also very
much about documenting & involving a community which takes pride in
experimenting with seeing things from alternative perspectives.

So in light of this:

We'd like to do a little experiment and see if a large (or even not so
large) group of people can collectively generate better quality random
numbers than an algorithm. Intuitively one would think and hope so,
but I fear our sample set will be heavily biased. Yet, even with that
the case, it will be fascinating to see if and how this might impact
an evolutionary process as it consumes our random choices. At some
point (once a sufficient amount of numbers has been collected/farmed -
with your incredible help, of course! :), it should be quite
enlightening to analyze the distribution itself in more detail.

## how?

The initial site is merely targeted at kickstarting the collection of
**our** random numbers (you can submit as many as you like). A simple,
public read-only API with optional seeding will be implemented in the
next few days to help integrating this stream into the above mentioned
GP setup.

The range of numbers supported is 0 .. 2^62
(4,611,686,018,427,387,904) and that limit is being enforced.

The app was built with:

* Clojure
* Leiningen
* Ring
* Compojure
* Hiccup
* Environ

## obtaining numbers

The site provides two simple means to get hold of submitted numbers via these routes:

### /random[?n=1..1000]

Returns between 1 and 1000 of the submitted numbers in pseudo-random order in either JSON, EDN or CSV formats (specify via `Accept` header). For example:

```bash
curl http://rnd.farm/random?n=10 -H 'Accept: application/json'
[617716,151278150479,11,8585785,6513205632056302,8756875767,1500450271,505,3388227013,5]

curl http://rnd.farm/random?n=10 -H 'Accept: application/edn'
[3784939215633 32447100556 7845 1234568526536 10 4562144123 367689452 22 545626323265 48376218205]

curl http://rnd.farm/random?n=10 # CSV is default
4329847338943798,3,963,9,2445,56378,14415245255555457,1337,76,283901
```

### /snapshot

This returns **all** submitted numbers in their original order as
plain text (one number per line).

## running locally

To start a local version on your own machine:

```bash
git clone https://github.com/postspectacular/rnd.farm.git
cd rnd.farm
RND_STREAM=random.txt lein ring server
```

After a few seconds, this will have launched the server on port 3000
and automatically opens the site in your browser. Numbers will be read
& written to the file given as `RND_STREAM`.

## license

Distributed under the [Apache Software License 2.0](http://www.apache.org/licenses/LICENSE-2.0).

Copyright © 2015 Karsten Schmidt
