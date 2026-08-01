package com.alexbarylski.jasperreporter.models;

import com.fasterxml.jackson.annotation.JsonProperty;

public class CompileRequest {

    private String source;
    private String target;
    private boolean force = false;

    @JsonProperty("dry_run")
    private boolean dryRun = false;

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }

    public String getTarget() {
        return target;
    }

    public void setTarget(String target) {
        this.target = target;
    }

    public boolean isForce() {
        return force;
    }

    public void setForce(boolean force) {
        this.force = force;
    }

    public boolean isDryRun() {
        return dryRun;
    }

    public void setDryRun(boolean dryRun) {
        this.dryRun = dryRun;
    }
}
