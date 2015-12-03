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
import hudson.FilePath;
import hudson.Launcher;
import hudson.LauncherDecorator;
import hudson.Proc;
import hudson.model.AbstractBuild;
import hudson.model.Executor;
import hudson.model.Node;
import hudson.model.Queue;
import hudson.model.Result;
import hudson.remoting.Channel;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import javax.servlet.ServletException;

/**
 *
 * @author tux3
 */
@Extension
public class AutoarmorLauncherDecorator extends LauncherDecorator {
    
    @Override
    public Launcher decorate(Launcher launcher, Node node) {
        Executor executor = Executor.currentExecutor();
        if(executor == null) {
            return launcher;
        }

        Queue.Executable exec = executor.getCurrentExecutable();
        if(!(exec instanceof AbstractBuild)) {
            return launcher;
        }

        AbstractBuild build = (AbstractBuild)exec;
        PrintStream logger = launcher.getListener().getLogger();
        AutoarmorConfig conf = AutoarmorConfig.getInstance();
        
        String mode = conf.getMode();
        boolean enforceMode;
        if (mode == null)
            throw new RuntimeException("Autoarmor: Mode is null! The config is broken, cancelling build.");
        if (mode.equals("disabled"))
            return launcher;
        else if (mode.equals("enforce"))
            enforceMode = true;
        else if (mode.equals("complain"))
            enforceMode = false;
        else
            throw new RuntimeException("Autoarmor: Mode is invalid! The config is broken, cancelling build.");
        
        AppArmorTools aatools;
        try {
            aatools = new AppArmorTools(launcher, build);
            if (!aatools.isEnabled()) {
                if (!conf.getIgnoreNoAA()) {
                    build.setResult(Result.FAILURE);
                    build.doStop();
                    throw new RuntimeException("Autoarmor: AppArmor is not active and we're configured to never build without it, cancelling build.");
                } else {
                    logger.println("Autoarmor: AppArmor is not active, the build will run unconfined.");
                    return launcher;
                }
            }
            if (!aatools.isWrapperWorking()) {
                build.setResult(Result.FAILURE);
                build.doStop();
                throw new RuntimeException("Autoarmor: The AppArmor wrapper self-test failed, cancelling the build");
            }
        } catch (InterruptedException|IOException|ServletException e) {
            build.setResult(Result.FAILURE);
            throw new RuntimeException("Autoarmor: Failed to check AppArmor installation, cancelling build.");
        }
        
        String workspaceRoot = node.getRootPath().getRemote()+"/workspace";
        String projectName = build.getProject().getName();
        logger.println("Autoarmor: Loading AppArmor profile for workspace "+workspaceRoot+"/"+projectName);
        if (!aatools.loadProfile(workspaceRoot, projectName, enforceMode)) {
            build.setResult(Result.FAILURE);
            throw new RuntimeException("Autoarmor: Failed to load AppArmor profile, cancelling build.");
        }
        
        launcher.getListener().getLogger().println("Autoarmor: Build confined by AppArmor in "+mode+" mode");
        return new ArmoredLauncher(launcher, build.getProject().getName());
    }
    
    /**
     * A decorated {@link Launcher} that runs all commands through an AppArmor
     * confinment wrapper
     */
    public class ArmoredLauncher extends Launcher {
        private final Launcher decorated;
        private final String[] wrapper;
        private final boolean maskWrapper;

        public ArmoredLauncher(Launcher decorated, String profileName) {
            super(decorated);
            this.decorated = decorated;
            wrapper = new String[]{"autoarmor-wrapper", profileName};
            maskWrapper = AutoarmorConfig.getInstance().getMaskCommands();
        }
        
        private String[] wrap(String[] cmd) {
            String[] newCmd = new String[wrapper.length + cmd.length];
            System.arraycopy(wrapper, 0, newCmd, 0, wrapper.length);
            System.arraycopy(cmd, 0, newCmd, wrapper.length, cmd.length);
            return newCmd;
        }
        
        private boolean[] wrap(boolean[] masks) {
            boolean[] newMasks = new boolean[wrapper.length + masks.length];
            System.arraycopy(masks, 0, newMasks, wrapper.length, masks.length);
            for (int i=0; i<wrapper.length; i++)
                newMasks[i] = true;
            return newMasks;
        }

        @Override
        public Proc launch(ProcStarter starter) throws IOException {
            List<String> cmds = starter.cmds();
            
            boolean quiet = starter.quiet();
            starter.quiet(true);
            if (!quiet)
                this.maskedPrintCommandLine(cmds, null, starter.pwd());
            
            cmds.addAll(0, Arrays.asList(wrapper));
            starter.cmds(cmds);
            
            if (maskWrapper) {
                boolean[] masks = starter.masks();
                if(masks == null) {
                    masks = new boolean[cmds.size()];
                    for (int i=0; i<wrapper.length; i++)
                        masks[i] = true;
                } else {
                    masks = wrap(masks);
                }
                starter.masks(masks);
            }
            
            return decorated.launch(starter);
        }

        @Override
        public Channel launchChannel(String[] cmd, OutputStream out, FilePath workDir, Map<String, String> envVars) throws IOException, InterruptedException {
            return decorated.launchChannel(wrap(cmd), out, workDir, envVars);
        }

        @Override
        public void kill(Map<String, String> modelEnvVars) throws IOException, InterruptedException {
            decorated.kill(modelEnvVars);
        }
       
    }
}
