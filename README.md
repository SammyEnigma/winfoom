# winfoom 

[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](https://github.com/ecovaci/winfoom/blob/master/LICENSE)
![Downloads](https://img.shields.io/github/downloads/ecovaci/winfoom/total)
![Downloads](https://img.shields.io/github/downloads/ecovaci/winfoom/latest/total)
![Release](https://img.shields.io/github/v/release/ecovaci/winfoom)

### Basic Proxy Facade for NTLM, Kerberos, SOCKS and Proxy Auto Config file proxies

If you find this application helpful, don't forget to give it a star ⭐

# Overview
Winfoom is an HTTP(s) proxy server facade that allows applications to authenticate through the following proxies: 

* NTLM or Kerberos HTTP authenticated proxy
* SOCKS version 4 or 5, with or without authentication
* Proxy Auto Config files - including Mozilla Firefox extension that is not part of original Netscape specification

typically used in corporate environments, without having to deal with the actual handshake.

A lot of software applications have problems when dealing with an authenticated proxy server's protocol. 
Winfoom sits between the corporate proxy and applications and offloads the authentication and the proxy's protocol, acting as a facade. 
This way, the software application will only have to deal with a basic proxy with no authentication.

An example of such a facade for NTLM proxies is [CNTLM](http://cntlm.sourceforge.net/)

# Getting Started
## Download Winfoom
### Download prepackaged
To try out Winfoom without needing to download the source and package it, check out the [releases](https://github.com/ecovaci/winfoom/releases) for a prepackaged `winfoom.zip`.
Winfoom is a Java application and requires a Java Runtime Environment (at least v11).

If it is not already installed on your system, you can download it from [AdoptOpenJDK](https://adoptopenjdk.net/) or, 
on Linux systems, use your package manager.

If, for certain reasons, you do not want to install Java globally on your system then download the JRE archive according
to your system architecture then unzip it in the Winfoom directory and rename it to `jdk`.

> 👉 Note: For Windows systems there is `winfoom-windows.zip` archive containing only the Windows executable files that comes with AdoptOpenJDK v11 (the `jdk` directory), therefore you don't need a Java Runtime Environment to be installed on your operation system.

### Build from source code
If you decide to build the executable *jar* file from the source code, you would need these prerequisites:
* Java JDK 11(+)
* Maven 3.x version (optional)

First download  the source code from [releases](https://github.com/ecovaci/winfoom/releases) and unzip it.

Then open a terminal and execute this command inside the `winfoom-x.x.x` directory:

```
 mvn clean package
```

or, if you did not install Maven, use the Maven Wrapper:

```
 .\mvnw clean package
```

Now you should have the generated executable *jar* file under the *target* directory.

## Run Winfoom

The prepackaged `winfoom.zip` contains the following executable files: `launch.bat`. 
* `launch.bat` launches the application (Windows systems)
* `launchGui.bat` launches the application in graphical mode (Windows systems)
* `launch.sh` launches the application (Linux/Macos systems, needs to be made executable)
* `foomcli.bat` manages the application (Windows systems)
* `foomcli.sh` manages the application (Linux/Macos systems, needs to be made executable)

On Windows systems, Winfoom can be launched by double-click on `launchGui.bat` or
from the command prompt:

`launch`

or, to run it in debug mode:

`launch --debug`

or, to run it in the graphical mode:

`launch --gui`

On Linux/Macos systems, there is no graphical mode available. Make sure the `*.sh` files are executable.
To run Winfoom, execute in a terminal:

`./launch.sh`

or, to run it in debug mode:

`./launch.sh --debug`

Winfoom can be launched with modified Java and system parameters by defining the environment variable `FOOM_ARGS`. For example:

`FOOM_ARGS=-Dsocket.soTimeout=10 -Dconnection.request.timeout=60`

> 👉 Note: It's a good idea to add the Winfoom's directory to the PATH environment variable.

## Winfoom's logs
The application log file is placed under `<user.home.dir>/.winfoom/logs` directory.

## Configuration
### User settings

#### The graphical mode (Windows only)
Winfoom has a graphical user interface that allows configuration.
 
The first thing to select is the proxy type:
1) `HTTP` - if the remote proxy is NTLM, KERBEROS or any other HTTP proxy
2) `SOCKS4` - if the remote proxy is SOCKS version 4
3) `SOCKS5` - if the remote proxy is SOCKS version 5
4) `PAC` - if the proxy is using a Proxy Auto Config file
5) `DIRECT` - no proxy, used for various testing environments

Then fill in the required fields. You can use the field's tooltip to get more information.

To put the application in `autostart` mode or `autodetect` mode see the `Settings` menu.

#### The command line mode (all systems)
If you run the application in non-graphical mode, Winfoom exposes an API accessible over HTTP on a local port (default 9999, configurable), 
that allows configuration and management.

The script `foomcli` provides easy access to this API. 

> 👉 Note: The `foomcli` script requires `curl`. The current version of WIndows 10 provides it by default.
> You can check if it is available by executing `curl --version` in your terminal. If you see something like `command not found` then you need to manually install it.

To get help about the usage execute:

`foomcli --help` (on Linux/Macos is `./foomcli.sh --help`)

> 👉 Note: You can move the script `foomcli` whatever location you want. It is not required to be in the Winfoom's directory.

_Examples_

After launching Winfoom, check the status of the local proxy facade:

`foomcli status`

If the local proxy is stopped, you cat start it with:

`foomcli start`

but before that, you need to configure it. Execute:

`foomcli config`

to get the current configuration. You'll get something like:

```
{
"proxyType" : "DIRECT",
"localPort" : 3129,
"proxyTestUrl" : "https://example.com"
}
```

The output is in JSON format. The name of the fields is self-descriptive. 
Suppose you want to configure Winfoom for a HTTP proxy. First, change the proxy type to HTTP with:

`foomcli config -t http`

Then, executing `foomcli config` again, the output is something like:

```
{
"proxyType" : "HTTP",
"proxyHost" : "",
"proxyPort" : 0,
"localPort" : 3129,
"proxyTestUrl" : "http://example.com"
}
```

To change the above values, copy the content of the output into a text file named, let's say, `http_config.json` 
in the same directory, and edit the field's values accordingly:

```
{
"proxyType" : "HTTP",
"proxyHost" : "192.168.0.105",
"proxyPort" : 80,
"localPort" : 3129,
"proxyTestUrl" : "http://example.com"
}
```

To load the new values, execute:

`foomcli config -f http_config.json`

and check the new configuration with `foomcli config` to be sure everything is as expected.

Now you can start the local proxy facade with `foomcli start`. 
At this moment you should be able to use Winfoom as a proxy facade in your browser.

If you want to shut down Winfoom then execute `foomcli shutdown`

---

On Linux/Macos, if the proxy type is HTTP, you need to set the `httpAuthProtocol` field, 
which is the proxy protocol: one of `NTLM, KERBEROS, BASIC` values. 

For Kerberos proxy protocol, the config JSON would look something like:

```
{
"proxyType" : "HTTP",
"proxyHost" : "auth.example.com",
"proxyPort" : 3128,
"proxyUsername" : "EXAMPLE.COM\\winfoom",
"proxyPassword" : "***",
"localPort" : 3129,
"proxyTestUrl" : "http://example.com",
"httpAuthProtocol" : "KERBEROS",
"krb5ConfFilepath" : "/etc/krb5.conf"
}
```

> 👉 Note: For Kerberos proxy protocol to work on Linux/Macos, your workstation must be properly configured.
> As an example for RHEL [see this](https://access.redhat.com/documentation/en-us/red_hat_enterprise_linux/7/html/system-level_authentication_guide/configuring_a_kerberos_5_client).
> Winfoom reads the Kerberos configuration from the `/etc/krb5.conf` location or the value of `KRB5_CONFIG` environment variable.

---

To put Winfoom in autostart mode first execute:

`foomcli settings`

The output would be something like:

```
{
"autostart" : false,
"autodetect" : false,
"appVersion" : "3.0.1",
"apiPort" : 9999
}
```

Copy the output into a file named, let's say, `settings.json` and modify accordingly:

```
{
"autostart" : true
}
```

Since we only modify the autostart option, the other fields are dropped off.

To load the new values, execute:

`foomcli settings -f settings.json`

then check the new settings with `foomcli settings`

> 👉 Note: If you modify the apiPort then you need to set the environment variable FOOM_LOCATION. 
> (For example FOOM_LOCATION=localhost:[your new port])

### System settings
The system settings configuration file is `<user.home.dir>/.winfoom/system.properties`.

_Please do not modify this file unless absolutely necessary. It is advisable to post your issue in [Issues Section](https://github.com/ecovaci/winfoom/issues) first._

The available settings:

| Key                |  Description      |  Type  |  Default value |
|--------------------|:-----------------:|:------:|:-------------:|
| maxConnections.perRoute |  Connection pool property:  max polled connections per route | Integer    | 20 |
| maxConnections  | Connection pool property: max polled connections  | Integer |600|
| internalBuffer.length |The max size of the entity buffer (bytes)|Integer |102400|
|connectionManager.clean.interval|The frequency of running purge idle on the connection manager pool (seconds)|Integer|30|
|connectionManager.idleTimeout|The connections idle timeout, to be purged by a scheduled task (seconds)|Integer|30|
|serverSocket.backlog|The maximum number of pending connections|Integer|1000|
|socket.soTimeout|The timeout for read/write through socket channel (seconds)|Integer|60|
|socket.connectTimeout|The timeout for socket connect (seconds)|Integer|20|
|pacScriptEngine.pool.maxTotal|The pacScriptEngine pool maximum total instances|Integer|100|
|pacScriptEngine.pool.minIdle|The pacScriptEngine pool min idle instances|Integer|20|
|connection.request.timeout|The timeout for request connection (seconds)|Integer|30|
|apiServer.request.timeout|The timeout for API commands (seconds)|Integer|10|
|kerberos.login.minInterval|The minimum interval successful Kerberos login is allowed (seconds)|Integer|30|

### Authentication
* For HTTP proxy type, Winfoom uses the current Windows user credentials to authenticate to the remote proxy. 
  On Linux/Macos you need to provide the user and password (or DOMAIN\user and password if the DOMAIN is required) 
* For SOCKS5 proxy type, the user/password need the be provided when required.

### Error codes
Starting with v2.6.0 Winfoom gives back the following HTTP error codes when there is no response from the remote proxy for various reasons:

| Proxy type         |  HTTP error code  |  When  |
|--------------------|:-----------------:|:------:|
|ALL|502|The remote proxy is not available|
|SOCKS/DIRECT|504|The giving address is not reachable|
|PAC|502|All remote proxies are blacklisted|
|ALL|500|Any other error|

### Test
To test it, open a browser, let's say Firefox and configure proxy like this:

![firefox](https://github.com/ecovaci/winfoom/blob/master/assets/img/firefox.png)

Now you should be able to access any URL without Firefox asking for credentials.

_If you don't have an available proxy, you still can test WinFoom by installing [WinGate](https://www.wingate.com/) and configure it to act 
as a NTML proxy._
   
# Coding Guidance

Please review these docs below about coding practices.

* [Google Java Style Guide](https://google.github.io/styleguide/javaguide.html)
* [Java Code Conventions](https://www.oracle.com/technetwork/java/codeconventions-150003.pdf)   

# Feedback

Any feedback or suggestions are welcome. 
It is hosted with an Apache 2.0 license so issues, forks and PRs are most appreciated.


