# Playback history and TV panel navigation

- Keep up to 1000 local positions in order of their last update.
- Recognize a TorrServer file by its HTTP(S) endpoint, torrent infohash and
  positive file index. A changed display name, parameter order or default port
  does not create another position. Native `torr`/`torrs` addresses share that key.
- Keep hosts, nondefault ports, proxy prefixes, torrent hashes and file indexes
  separate. Ambiguous links retain their original identity.
- Migrate the existing serialized positions map without replacing valid entries
  with zero. For duplicate aliases the last stored entry wins.
- A local copy of actual external-player progress does not change Playlist API
  positions, callbacks or explicit launch positions. Store clip-relative playback
  as an absolute file position locally; never record unvisited playlist items.
- M3U does not invent a caller-supplied zero position. The server keeps control
  over `fromlast` ordering, while the selected file can resume from local history.
- On TV, closing the final picker returns to its opening control. Moving between
  pickers does not flash or focus controls behind the new picker.
- Back from a panel opened in More returns to More on the same row. Closing a
  panel outside its Back navigation still exits the panel sequence.
- A background dismissal or new media launch cannot raise old controls.

Validation covers legacy migration, identity separation, exact history limits,
millisecond positions, launch-position precedence and activity/dialog navigation.
Physical D-pad and playback checks remain separate from automated verification.
