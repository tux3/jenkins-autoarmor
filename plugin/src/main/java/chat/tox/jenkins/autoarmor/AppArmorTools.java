/*
 * The MIT License
 *
 * Copyright 2015 tux3.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package chat.tox.jenkins.autoarmor;

import hudson.Launcher;
import hudson.model.AbstractBuild;
import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Checks the status of AppArmor with a given Launcher
 * The launcher must not be already confined by AppArmor
 */
public class AppArmorTools {
    private final Launcher launcher;
    private final AbstractBuild build;
    private final Launcher.ProcStarter baseProcStarter;
    
    public AppArmorTools(Launcher launcher, AbstractBuild build) 
                throws InterruptedException, IOException {
        this.launcher = launcher;
        this.build = build;
        baseProcStarter = launcher.launch()
                .envs(build.getEnvironment(launcher.getListener()))
                .quiet(AutoarmorConfig.getInstance().getMaskCommands())
                .pwd("/");
    }
    
    private int run(String cmd) throws IOException, InterruptedException {
        return launcher.launch(baseProcStarter.cmdAsSingleString(cmd)).join();
    }
    
    public boolean isEnabled() throws IOException, InterruptedException {
        int ret = run("grep Y /sys/module/apparmor/parameters/enabled");
        return ret==0;
    }
    
    public boolean isWrapperWorking() {
        try {
            return run("autoarmor-wrapper --self-test") == 0;
        } catch (Exception e) {
            return false;
        }
    }
    
    public boolean loadProfile(String workspaceRoot, String profile, boolean enforce) {
        String mode = enforce ? "enforce" : "complain";
        try {
            return run("autoarmor-genprof \""+workspaceRoot+"\" \""+profile+"\" "+mode) == 0;
        } catch (Exception e) {
            return false;
        }
    }
}
