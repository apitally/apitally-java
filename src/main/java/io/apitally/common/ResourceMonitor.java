package io.apitally.common;

import io.apitally.common.dto.ResourceUsage;
import oshi.SystemInfo;
import oshi.software.os.OSProcess;

public class ResourceMonitor {
    private final SystemInfo systemInfo = new SystemInfo();
    private OSProcess previousSnapshot;

    public ResourceUsage getCpuMemoryUsage() {
        try {
            int pid = (int) ProcessHandle.current().pid();
            OSProcess currentProcess = systemInfo.getOperatingSystem().getProcess(pid);
            if (currentProcess == null) {
                return null;
            }

            if (previousSnapshot == null) {
                previousSnapshot = currentProcess;
                return null;
            }

            double cpuPercent = 100.0 * currentProcess.getProcessCpuLoadBetweenTicks(previousSnapshot);
            long memoryRss = currentProcess.getResidentSetSize();
            previousSnapshot = currentProcess;

            return new ResourceUsage(cpuPercent, memoryRss);
        } catch (Exception e) {
            return null;
        }
    }
}
