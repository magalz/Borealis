<!-- markdownlint-disable MD041 -- appended to the generated release notes, which use level 2 headings -->
## Compatibility

* Tested with LuminalShine 26.09.1.
* Based on Moonlight 12.2 ([moonlight-android](https://github.com/moonlight-stream/moonlight-android)).
* Streaming protocol library: the Borealis fork of moonlight-common-c, tag `borealis/874ac95-1` (upstream base `874ac95`).

### Known host issues

* [NortheBridge/luminalshine#194](https://github.com/NortheBridge/luminalshine/issues/194): unknown GET paths such as `/unpair` return a stray second HTTP response on the same connection.
* [NortheBridge/luminalshine#198](https://github.com/NortheBridge/luminalshine/issues/198): after a client disconnects from a Desktop session without "Quit Session", the host can stall in session teardown and restart.
