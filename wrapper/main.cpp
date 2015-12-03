#include <iostream>
#include <string>
#include <unistd.h>
#include <fcntl.h>
#include <sys/types.h>
#include <sys/wait.h>
#include <sys/stat.h>
#include <sys/apparmor.h>
#include <errno.h>

using namespace std;

bool self_test();
void help();

static const string job_profile_prefix = "autoarmor-job-";

/// Simple unprivileged wrapper that confines itself and runs the given command
int main(int argc, char* argv[])
{
    if (argc < 2) {
        help();
        return 1;
    }

    if (argv[1] == string("--self-test"))
        return self_test() ? 0 : 1;

    if (argc < 3) {
        help();
        return 1;
    }

    string profile = job_profile_prefix+argv[1];
    if (aa_change_onexec(profile.c_str()) != 0) {
        cout << "autoarmor-wrapper: Failed to change to profile "<<profile<<endl;
        return 1;
    }
    if (execvp(argv[2], argv+2) == -1) {
        cout << "autoarmor-wrapper: Failed to run command"<<endl;
        cout << "errno"<<errno<<endl;
        return 1;
    }
}

void help()
{
    cout << "Usage: autoarmor-wrapper --self-test\n"
            "or     autoarmor-wrapper profile command [args...]" << endl;
}

/// Check if we can run autoarmor-genprof and ask it to self-test too
/// Then confine ourselves and check we can't read /etc/passwd
bool self_test()
{
    // First run genprof in a thread and make sure it succeeds
    pid_t cpid = fork();
    if (!cpid) {
        if (execlp("autoarmor-genprof", "autoarmor-genprof", "--self-test", 0) == -1) {
            cout << "autoarmor-wrapper: Failed to run autoarmor-genprof, self-test failed"<<endl;
            _exit(1);
        }
    }
    int ret;
    wait(&ret);
    if (!WIFEXITED(ret) || WEXITSTATUS(ret) != 0)
        return false;

    // Now check that we can self-confine
    if (aa_change_profile("autoarmor-denyall") != 0) {
        cout << "autoarmor-wrapper: Failed to self-confine, self-test failed"<<endl;
        return false;
    }
    if (open("/etc/passwd", O_RDONLY) != -1) {
        cout << "autoarmor-wrapper: Failed self-confinement sanity check, self-test failed"<<endl;
        return false;
    }

    return true;
}
