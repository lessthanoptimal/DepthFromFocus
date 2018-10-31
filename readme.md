# Introduction

This is a quick experiment to see if I can extract depth information
by adjusting the focus in a webcam. I've thought about using focus for years
but never actually tried. Inorder to keep this as a short and fun project I'm
hoping for crude proof of concept results. if I don't get a completely random point cloud
that will be considered success.

Here are my fundamental assumptions

1) Calibrated the camera by assuming a fixed focus
2) Assumed that objects which are in focus have sharper edges
3) That there is an inverse linear relationship between the focus control values and depth

The very first assumption is clearly a bad one because I'm changing the focus. However,
it will put the calibration in the correct ball park and based on the results is unlikely
to be the biggest source of error.

Second assumption is partially correct. Objects in focus do have that characteristic
but you can't rely on it alone.

The final assumption is a big one and could be screwing up the results. Determining the real
focal length at each value is more work than I'm willing to do right now.

To make things easier I've included a data of images from my Logitech webcam in this repository. 
A V4L library I've been working is what was used to control the camera. It's been included
as a git subproject.

# Quick Start

This has been tested on Ubuntu 16.04. You might need to install some other libraries.

```commandline
git submodule init
git submodule update
cd webcam;./build_release.sh
```

Then load the project up in IntelliJ and run DepthByFocusApp



