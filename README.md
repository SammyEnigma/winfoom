# winfoom
### Basic Proxy Facade for NTLM and Kerberos proxies

[![Build Status](https://travis-ci.com/ecovaci/winfoom.svg?branch=master)](https://travis-ci.com/github/ecovaci/winfoom)

Winfoom is an HTTP(s) proxy server facade that allows applications to authenticate through a NTML/Kerberos HTTP authenticated proxy server or SOCKS 5 proxy server, typically used in corporate environments, 
without having to deal with the actual handshake.

A lot of software applications have problems when dealing with an authenticated proxy server's protocol. Winfoom sits between the corporate proxy and applications and offloads the authentication and the proxy's protocol, acting as a facade. This way, the software application will only have to deal with a basic proxy with no authentication.

An example of such a facade for NTLM proxies is [CNTLM](http://cntlm.sourceforge.net/)

### Instalation

> 👉 Note: Winfoom only works on Windows OS!

Winfoom is a Java application and comes with a release that includes a Java environment [AdoptOpenJDK](https://github.com/AdoptOpenJDK/openjdk11-binaries/releases/download/jdk-11.0.5%2B10/OpenJDK11U-jdk_x64_windows_hotspot_11.0.5_10.msi),
so you don't have to install anything. 

Starting with v1.5.1, the release contains a single executable file: `launch.bat`. 
Available commands:
 
* `launch.bat` launches the application using the bundled JRE.
* `launch.bat --debug` launches the application using the bundled JRE in debug mode.
* `launch.bat --systemjre` launches the application using your system JRE - you'll need a JRE v.11 (at least).
* `launch.bat --debug --systemjre`  launches the application using your system JRE in debug mode.

The installation is pretty basic, just unzip the content of the released archive then double-click on `launch.bat` file.

The application log file is placed under `logs` directory.

### Configuration

Winfoom has a graphical user interface that allows the user to configure the proxy type (HTTP or SOCKS5), the proxy host, port and the URL for testing the settings.
These fields are pre-filled with the values gathered from your system.

For SOCKS proxy type, if authentication is required use the `Config` button to provide user/password. 

To test it, open a browser, let's say Firefox and configure proxy like this:

![firefox](https://github.com/ecovaci/winfoom/blob/master/assets/img/firefox.png)

Now you should be able to access any URL without Firefox asking for credentials.

_If you don't have an available proxy, you still can test WinFoom by installing [WinGate](https://www.wingate.com/) and configure it to act 
as a NTML proxy._

> 👉 Note: For HTTP proxy type, Winfoom uses the current user credentials to authenticate to the remote proxy, there is no need to provide them!

### Build from source code

If you decide to build the executable *jar* file from source code, you would need these prerequisites:

* Java JDK 11(+)
* Maven 3.x version
* Git for Windows

Then open a terminal and execute the commands:

1) `git clone https://github.com/ecovaci/winfoom.git`
2) `cd winfoom`
3) `mvn clean package`

Now you should have the generated executable *jar* file under the *target* directory.

### Todo

   - Performance tests.
   
### Coding Guidance

Please review these docs below about coding practices.

* [Google Java Style Guide](https://google.github.io/styleguide/javaguide.html)
* [Java Code Conventions](https://www.oracle.com/technetwork/java/codeconventions-150003.pdf)   

### Feedback

Any feedback or suggestions are welcome. 
It is hosted with an Apache 2.0 license so issues, forks and PRs are most appreciated.


