# RUBTClient

Our program, RUBTClient, runs with 2 arguments, the name of the torrent and the file
save name. The base of the program runs the Torrent Handler object, which starts by reading the
torrent file. It is responsible for handling/ coordinating the different instances of each class and
tasks relating from the peer, tracker and messages. It follows a flow of direction from the torrent
handler, where as it will download from multiple peers. In addition, if one wants to pause the
download simply input “quit” in the terminal or close the window itself. If one wants to continue
the download, one simply reruns the program
