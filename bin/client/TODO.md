TODO
====

Phase 2
-------

- ~~upload~~
- ~~time download~~
- ~~contact tracker with STOPPED when client exits~~

Phase 3
-------

- Rarest-piece-first algorithm
- Choking & optimistic unchoking (From assignment:  Throughput measurement is necessary to accomplish correct (un)choking behavior.  The client should be able to maintain connections to all available peers, though not all peers need to be uploading/downloading simultaneously.)
- Extra credit will be available to groups that develop a Graphical User Interface (GUI) for their client.  The GUI should have complete functionality: displaying all relevant information about the client, allowing user input, and be aesthetically pleasing.  The extra credit GUI has a maximum value of 15% of the project grade (115% with full credit).  To achieve the full 15%, the GUI will need to be very well-designed and -implemented.

TorrentHandler
--------------

- Right now, made to process a queue of Callable<Void> objects. This is basically a queue of blocks of code to execute.
- Instead of putting messages on the queue, and then having to go see how each message is handled to find what will happen on the TorrentHandler thread, it now puts in a block of code to execute on the queue, which directly defines what will happen. This is mostly just makes it easier to debug/ find what will happen. It would be cleaner if Java's anonymous classes syntax weren't so verbose.
- This tracker also seems to just react to events, which are called by Peer threads (more specifically, Peer reader threads). It holds some state by keeping a list of all the Peers, but this probably has to be extended in order to be able to accurately greedly request pieces in the end game and to be able to request the rarest pieces first.

Tracker
-------

- Maybe have stateless communication with the tracker object?
- This way the tracker communication would more closely represent the HTTP protocol?
- Also simpler to use since we just need to send events to the tracker

Bitfield
--------

- Do we NEED Bitfield.java, or can we just use BitSet?
- Minimizing depedencies is nice


Messages
--------

- Make messages have two pointers in order to avoid copying whole piece arrays. Then make it so that when writing to a socket, the two arrays are sent in sequence. *(interfacers are nice)*
	1. For header array
	2. For payload array
