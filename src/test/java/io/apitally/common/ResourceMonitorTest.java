package io.apitally.common;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.apitally.common.dto.ResourceUsage;
import org.junit.jupiter.api.Test;

class ResourceMonitorTest {

    @Test
    void testGetCpuMemoryUsage() throws InterruptedException {
        ResourceMonitor resourceMonitor = new ResourceMonitor();

        ResourceUsage firstResult = resourceMonitor.getCpuMemoryUsage();
        assertNull(firstResult);

        Thread.sleep(100);

        ResourceUsage secondResult = resourceMonitor.getCpuMemoryUsage();
        assertNotNull(secondResult);
        assertTrue(secondResult.getCpuPercent() >= 0);
        assertTrue(secondResult.getMemoryRss() > 0);
    }
}
