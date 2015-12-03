# AutoArmor

AutoArmor is a Jenkins plugin to isolate Jenkins jobs from each other and from the system using AppArmor.

## Installation

Build the plugin using Maven, and install it on the Jenkins master.<br/>
Then on all the Jenkins slaves that you want to protect:

- Install AppArmor, 
- Build and the autoarmor-genprof and autoarmor-wrapper using CMake
- Install both tools in the system PATH, and set the autoarmor-genprof helper setuid-root

And finally in the Jenkins master's system configuration set the AppArmor mode to Enforce to start using AppArmor.

## AppArmor profile configuration

autoarmor-genprof will generate AppArmor profiles on the fly for new Jenkins jobs,
but the profiles can be edited manually. Once a profile exists, autoarmor-genprof will not overwrite it.
The default profile is embedded in autoarmor-genprof.

All the profiles can be found in /etc/apparmor.d/autoarmor/.
