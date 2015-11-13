Airspy Library for Android
==========================

This repository is a ported version of Benjamin Vernoux's and 
Youssef Touil's libairspy
([https://github.com/airspy/host/tree/master/libairspy]
(https://github.com/airspy/host/tree/master/libairspy))
library to work with Android 3.1+.

See [http://tech.mantz-it.com](http://tech.mantz-it.com) and @dennismantz for updates.


Implemented Features
--------------------
* Open Airspy (including the USB permission request)
* Reading Board ID from Airspy
* Reading Version from Airspy
* Reading Part ID and Serial Number from Airspy
* Setting Sample Rate of Airspy
* Setting Frequency of Airspy
* Setting VGA Gain of Airspy
* Setting LNA Gain of Airspy
* Setting Mixer Gain of Airspy
* Receiving from the Airspy using a BlockingQueue
* Get Transmission statistics
* Example App that shows how to use the library


Tested Devices
--------------

|    Device          | Does it work? | Comments                                  |     Tester       |
|:------------------:|:-------------:|:-----------------------------------------:|:----------------:|
| Nexus 7 2012       |  yes and no   | IQ not working (performance issue)        | demantz          |
| Nexus 5            |  yes and no   | IQ not working (performance issue)        | demantz          |



Known Issues
------------
* packing not working
* performance issues


Installation / Usage
--------------------
Build the library and the example app by using Androis Studio:
* Import existing project (root of the git repository)
* Build
* The aar library file will be under airspy_android/build/outputs/aar/airspy_android-release.aar

If you want to use the library in your own app, just create a new module in your
Android Studio project and select 'Import .JAR/.AAR'. Name it airspy_android.
Then add to the gradle.build file of your main module:

dependencies {
    compile project(':airspy_android')
}

The airspy_android.aar and the Airspy_Test.apk files are also in this repository
so that they can be used without building them. But they won't be synched to the
latest code base all the time.

Use the example application to test the library on your device and trouble shoot
any problems. It has the option to show the logcat output!

License
-------
This library is free software; you can redistribute it and/or
modify it under the terms of the GNU General Public
License as published by the Free Software Foundation; either
version 2 of the License, or (at your option) any later version.
[http://www.gnu.org/licenses/gpl.html](http://www.gnu.org/licenses/gpl.html) GPL version 2 or higher

principal author: Dennis Mantz <dennis.mantzgooglemail.com>

principal author of libairspy: Benjamin Vernoux <bvernoux@airspy.com> and Youssef Touil <youssef@airspy.com>
