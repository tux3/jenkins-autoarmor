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
import hudson.util.ListBoxModel;
import hudson.util.ListBoxModel.Option;
import jenkins.model.GlobalConfiguration;

@Extension
public final class AutoarmorConfig extends GlobalConfiguration {    
    private boolean ignoreNoAA;
    private boolean maskCommands;
    private String mode;
    
    public AutoarmorConfig() {
        ignoreNoAA = true;
        maskCommands = true;
        mode = "disabled";
        load();
    }
    
    @Override
    public boolean configure(org.kohsuke.stapler.StaplerRequest req,
                net.sf.json.JSONObject json) {
        ignoreNoAA = json.optBoolean("ignoreNoAA");
        maskCommands = json.optBoolean("maskCommands");
        mode = json.getString("mode");
        save();
        return true;
    }
    
    public ListBoxModel doFillModeItems() {
        return new ListBoxModel(
            new Option("Disabled", "disabled", mode == null || mode.equals("disabled")),
            new Option("Complain", "complain", mode != null && mode.equals("complain")),
            new Option("Enforce", "enforce", mode != null && mode.equals("enforce")));
    }
    
    public boolean getIgnoreNoAA() {
        return ignoreNoAA;
    }
    
    public boolean getMaskCommands() {
        return maskCommands;
    }
    
    public String getMode() {
        return mode;
    }
    
    public static AutoarmorConfig getInstance() {
        return GlobalConfiguration.all().get(AutoarmorConfig.class);
    }
}
