#include <iostream>
#include <string>
#include <unistd.h>
#include <sys/types.h>
#include <sys/stat.h>
#include <sys/wait.h>
#include <fcntl.h>
#include <errno.h>

using namespace std;

bool load_profile(string name, bool enforce);
bool write_base_profile();
bool write_denyall_profile();
bool write_job_profile(string basename, string workspace_base);
bool write_profile(string name);
bool write_profile(string name, string rules);
bool self_test();
bool dir_exists(string path);
void help();

static const char* profiles_dir = "/etc/apparmor.d/autoarmor/";
static const char* job_profile_prefix = "autoarmor-job-";

/// Setuid tool that generates AppArmor profiles for Jenkins jobs
int main(int argc, char* argv[])
{
    if (argc < 2) {
        help();
        return 1;
    }

    if (setuid(0) != 0 || getuid() != 0) {
        cout << "autoarmor-genprof: Needs to be run as root (set the setuid-root bits!)"<<endl;
        return 1;
    }

    if (!dir_exists(profiles_dir)) {
        if (mkdir(profiles_dir, 0755) != 0) {
            cout << "autoarmor-genprof: Couldn't create AppArmor profile folder "<<profiles_dir<<endl;
            return 1;
        }
    }

    if (!write_denyall_profile()) {
        cout << "autoarmor-genprof: Failed to write deny-all profile"<<endl;
        return 1;
    }
    if (!load_profile("autoarmor-denyall", true))
        return 1;

    if (argv[1] == string("--self-test"))
        return self_test() ? 0 : 1;

    if (argc != 4) {
        help();
        return 1;
    }

    string workspace_root = argv[1], profile = argv[2], mode = argv[3];
    if (mode != "complain" && mode != "enforce") {
        cout << "autoarmor-genprof: Invalid mode "<<mode<<endl;
        return 1;
    }
    bool enforce_mode = mode == "enforce";

    if (!dir_exists(workspace_root)) {
        cout << "autoarmor-genprof: Workspace root folder "<<workspace_root<<" doesn't exist"<<endl;
        return 1;
    }

    if (!write_job_profile(profile, workspace_root))
        return 1;
    if (!load_profile(job_profile_prefix+profile, enforce_mode))
        return 1;

    return 0;
}

void help()
{
    cout << "Usage: autoarmor-genprof --self-test\n"
            "or     autoarmor-genprof workspace-root profile mode" << endl;
}

bool self_test()
{
    // For now, just setting up our invariants in main() will count as a successful self_test
    return true;
}

bool dir_exists(string path)
{
    struct stat s = {0};

    if (stat(path.c_str(), &s) != 0)
        return false;
    return S_ISDIR(s.st_mode);
}

/// Writes a new auto-generated AppArmor profile for a job
/// if a profile with the same name doesn't already exist
bool write_profile(string name)
{
    string rules = "#include <tunables/global>\n"
                   "profile "+name+"{\n"
                   "  #include <autoarmor-base>\n"
                   "}\n";
    return write_profile(name, rules);
}

/// Writes a new auto-generated AppArmor profile for a job
/// if a profile with the same name doesn't already exist
bool write_profile(string name, string rules)
{
    string path = string(profiles_dir)+name;

    int fd = open(path.c_str(), O_WRONLY | O_CREAT | O_EXCL, 0644);
    if (fd == -1)
        return errno == EEXIST;

    if (write(fd, rules.c_str(), rules.size()) != rules.size())
        return false;

    close(fd);
    return true;
}

bool write_denyall_profile()
{
    string name = "autoarmor-denyall";
    string rules = "profile "+name+" {\n"
                    "  /dev/null rw,\n"
                    "  deny /etc/passwd r,\n" // Quiet deny for sanity-checking
                    "}\n";
    return write_profile(name, rules);
}

bool write_job_profile(string basename, string workspace_base)
{
    string fullname = job_profile_prefix+basename;
    string rules =  "#include <tunables/global>\n"
                    "profile "+fullname+" {\n"
                    "  #include <abstractions/base>\n"
                    "  #include <abstractions/consoles>\n"
                    "  capability dac_override,\n"
                    "  capability setuid,\n"
                    "  capability setgid,\n"

                    "  /tmp/** rwix,\n"
                    "  /var/tmp/** rw,\n"
                    "  /var/tmp/ r,\n"
                    "  deny /tmp/hudson*.sh w,\n"

                    "  /proc/*/fd/ r,\n"
                    "  /sys/devices/system/cpu/ r,\n"
                    "  /proc/sys/net/ipv4/ip_local_port_range r,\n"
                    "  /proc/*/net/if_inet6 r,\n"
                    "  /proc/*/net/ipv6_route r,\n"

                    "  /etc/** r,\n"

                    "  /{,s}bin/*      rix,\n"
                    "  /usr/{,s}bin/*  rix,\n"
                    "  /usr/{,local/}lib/** rix,\n"

                    "  /usr/ r,\n"
                    "  /lib/ r,\n"
                    "  /bin/ r,\n"
                    "  /usr/local/ r,\n"
                    "  /usr/local/bin/ r,\n"
                    "  /usr/local/lib/ r,\n"
                    "  /usr/games/ r,\n"
                    "  /usr/lib/ r,\n"
                    "  /usr/bin/ r,\n"
                    "  /usr/share/** r,\n"
                    "  /usr/{,local/}include/** r,\n"
                    "  /usr/{,local/}include/ r,\n"
                    "  /usr/i686-w64-mingw32/** r,\n"
                    "  /usr/x86_64-w64-mingw32/** r,\n"
                    "  /run/resolvconf/** r,\n"
                    "  /opt/android-{ndk,sdk}/ rix,\n"
                    "  /opt/android-{ndk,sdk}/** rix,\n"
                    "  /usr/share/sbt-launcher-packaging/** rix,\n"
                    "  /usr/sbin/pbuilder pix,\n"
                    "  "+workspace_base+'/'+basename+"/** rwlkix,\n"
                    "  "+workspace_base+'/'+basename+"/ r,\n"
                    "  /opt/pbuilder-local-repo/repo/** rw,\n"

                    "  deny @{HOME}/.wget-hsts rw,\n"
                    "  @{HOME}/.rpmdb/** rw,\n"
                    "  @{HOME}/cache/** rw,\n"
                    "  @{HOME}/cache/ rw,\n"
                    "  @{HOME}/.sbt/boot/** rwlk,\n"
                    "  @{HOME}/.sbt/boot/ rw,\n"
                    "  @{HOME}/.ivy2/** rwlk,\n"
                    "  @{HOME}/.ivy2/ rw,\n"
                    "  @{HOME}/.gradle/** rwlk,\n"
                    "  @{HOME}/.gradle/ rw,\n"
                    "  @{HOME}/.zinc/** rwlk,\n"
                    "  @{HOME}/.zinc/ rw,\n"
                    "  @{HOME}/.m2/** rwlk,\n"
                    "  @{HOME}/.m2/ rw,\n"
                    "}\n";

    return write_profile(fullname, rules);
}

bool load_profile(string name, bool enforce)
{
    const char* toolname = enforce ? "/usr/sbin/aa-enforce" : "/usr/sbin/aa-complain";
    string path = profiles_dir+name;

    pid_t cpid = fork();
    if (!cpid) {
        if (execlp(toolname, toolname, path.c_str(), 0) == -1) {
            cout << "autoarmor-genprof: Failed to exec "<<toolname<<", couldn't load profile "<<path<<endl;
            cout << "errno "<<errno<<endl;
            _exit(1);
        }
    }
    int ret;
    wait(&ret);
    if (!WIFEXITED(ret) || WEXITSTATUS(ret) != 0) {
        cout << "autoarmor-genprof: "<<toolname<<" failed, couldn't load profile "<<path<<endl;
        return false;
    }

    return true;
}
