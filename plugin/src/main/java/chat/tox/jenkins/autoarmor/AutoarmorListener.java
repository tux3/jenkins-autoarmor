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
import hudson.Extension;
import hudson.Launcher;
import hudson.model.AbstractBuild;
import hudson.model.BuildListener;
import hudson.model.Environment;
import hudson.model.Result;
import hudson.model.Run;
import hudson.model.listeners.RunListener;
import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.servlet.ServletException;

/**
 * Listens for confined builds and run a basic sanity check before they start
 */
@Extension
public class AutoarmorListener extends RunListener<Run<?,?>> {

    public AutoarmorListener() {
    }
    
    @Override
    public Environment setUpEnvironment(AbstractBuild build,
                           Launcher launcher,
                           BuildListener listener) throws IOException, InterruptedException {
        
        Environment env = new Environment() {};
        
        if (!(launcher instanceof AutoarmorLauncherDecorator.ArmoredLauncher))
            return env;
        
        Launcher.ProcStarter baseProc = launcher.launch()
                .envs(build.getEnvironment(listener))
                .pwd(build.getWorkspace())
                .quiet(AutoarmorConfig.getInstance().getMaskCommands());
        
        int ret = launcher.launch(baseProc.cmdAsSingleString("cat /etc/passwd > /dev/null")).join();
        if (ret == 0) {
            listener.getLogger().println("Autoarmor: Sanity-check failed, we could read /etc/passwd on a confined build! Aborting build.");
            build.setResult(Result.FAILURE);
            try {
                build.doStop();
            } catch (ServletException ex) {
            }
            return null;
        } else {
            listener.getLogger().println("Autoarmor: Sanity-check passed, confinement is working!");
        }
        
        return env;
    }
}

