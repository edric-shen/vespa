// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.clustercontroller.core;

public interface RemoteClusterControllerTaskScheduler {

    void schedule(RemoteClusterControllerTask task);

}
