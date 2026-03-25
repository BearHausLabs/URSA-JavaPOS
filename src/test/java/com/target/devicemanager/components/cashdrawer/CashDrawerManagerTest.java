package com.target.devicemanager.components.cashdrawer;

import com.target.devicemanager.common.DeviceLifecycleResponse;
import com.target.devicemanager.common.DeviceLifecycleState;
import com.target.devicemanager.common.DynamicDevice;
import com.target.devicemanager.common.entities.DeviceError;
import com.target.devicemanager.common.entities.DeviceException;
import com.target.devicemanager.common.entities.DeviceHealth;
import com.target.devicemanager.common.entities.DeviceHealthResponse;
import com.target.devicemanager.components.cashdrawer.entities.CashDrawerError;
import jpos.CashDrawer;
import jpos.JposConst;
import jpos.JposException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;

import java.util.*;
import java.util.concurrent.Callable;
import java.util.concurrent.locks.Lock;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
public class CashDrawerManagerTest {

    private CashDrawerManager cashDrawerManager;

    private CashDrawerManager cashDrawerManagerCache;

    @Mock
    private CashDrawerDevice mockCashDrawerDevice;
    @Mock
    private Lock mockCashDrawerLock;
    @Mock
    private CacheManager mockCacheManager;
    @Mock
    private DynamicDevice<CashDrawer> mockDynamicDevice;

    private final Cache testCache = new Cache() {
        final Map<Object, Object> cacheMap = new HashMap<>();

        @Override
        public String getName() {
            return "cashDrawerHealth";
        }

        @Override
        public Object getNativeCache() {
            return null;
        }

        @Override
        public ValueWrapper get(Object key) {
            if(cacheMap.containsKey(key)) {
                return () -> cacheMap.get(key);
            } else {
                return null;
            }
        }

        @Override
        public <T> T get(Object key, Class<T> type) {
            return null;
        }

        @Override
        public <T> T get(Object key, Callable<T> valueLoader) {
            return null;
        }

        @Override
        public void put(Object key, Object value) {
            cacheMap.put(key, value);
        }

        @Override
        public void evict(Object key) {

        }

        @Override
        public void clear() {

        }
    };

    @BeforeEach
    public void testInitialize() {
        when(mockCashDrawerDevice.getDrawerId()).thenReturn(1);
        when(mockCashDrawerDevice.getDrawerType()).thenReturn("DRAWER_1");
        when(mockCashDrawerDevice.getDynamicDevice()).thenReturn(mockDynamicDevice);
        List<CashDrawerDevice> drawers = List.of(mockCashDrawerDevice);
        cashDrawerManager = new CashDrawerManager(drawers, mockCashDrawerLock);
        cashDrawerManagerCache = new CashDrawerManager(drawers, mockCashDrawerLock, mockCacheManager);
    }

    @Test
    public void ctor_WhenCashDrawerListAndLockAreNull_ThrowsException() {
        try {
            new CashDrawerManager(null, null);
        } catch (IllegalArgumentException iae) {
            assertEquals("cashDrawers cannot be null or empty", iae.getMessage());
            return;
        }

        fail("Expected Exception, but got none");
    }

    @Test
    public void ctor_WhenCashDrawerListIsNull_ThrowsException() {
        try {
            new CashDrawerManager(null, mockCashDrawerLock);
        } catch (IllegalArgumentException iae) {
            assertEquals("cashDrawers cannot be null or empty", iae.getMessage());
            return;
        }

        fail("Expected Exception, but got none");
    }

    @Test
    public void ctor_WhenCashDrawerListIsEmpty_ThrowsException() {
        try {
            new CashDrawerManager(new ArrayList<>(), mockCashDrawerLock);
        } catch (IllegalArgumentException iae) {
            assertEquals("cashDrawers cannot be null or empty", iae.getMessage());
            return;
        }

        fail("Expected Exception, but got none");
    }

    @Test
    public void ctor_WhenCashDrawerLockIsNull_ThrowsException() {
        try {
            new CashDrawerManager(List.of(mockCashDrawerDevice), null);
        } catch (IllegalArgumentException iae) {
            assertEquals("cashDrawerLock cannot be null", iae.getMessage());
            return;
        }

        fail("Expected Exception, but got none");
    }

    @Test
    public void ctor_WhenCashDrawerListAndLockAreNotNull_DoesNotThrowException() {
        try {
            new CashDrawerManager(List.of(mockCashDrawerDevice), mockCashDrawerLock);
        } catch(Exception exception) {
            fail("Existing Device Argument should not result in an Exception");
        }
    }

    @Test
    public void connect_WhenLockSucceeds_Connects() {
        //arrange
        when(mockCashDrawerDevice.tryLock()).thenReturn(true);

        //act
        cashDrawerManager.connect();

        //assert
        verify(mockCashDrawerDevice).connect();
        verify(mockCashDrawerDevice).unlock();
    }

    @Test
    public void connect_WhenLockFails_DoesNotConnect() {
        //arrange
        when(mockCashDrawerDevice.tryLock()).thenReturn(false);

        //act
        cashDrawerManager.connect();

        //assert
        verify(mockCashDrawerDevice, never()).connect();
        verify(mockCashDrawerDevice, never()).unlock();
    }

    @Test
    public void reconnect_WhenLockSucceeds_Reconnects() {
        //arrange
        when(mockCashDrawerDevice.tryLock()).thenReturn(true);
        when(mockCashDrawerDevice.connect()).thenReturn(true);

        //act
        try {
            cashDrawerManager.reconnectDevice();
        } catch (DeviceException deviceException) {
            fail("reconnectDevice should not result in an Exception");
        }


        //assert
        verify(mockCashDrawerDevice).disconnect();
        verify(mockCashDrawerDevice).connect();
        verify(mockCashDrawerDevice).unlock();
    }

    @Test
    public void reconnect_WhenLockFails_DoesNotReconnect() {
        //arrange
        when(mockCashDrawerDevice.tryLock()).thenReturn(false);

        //act
        try {
            cashDrawerManager.reconnectDevice();
        } catch (DeviceException deviceException) {
            assertEquals(DeviceError.DEVICE_BUSY, deviceException.getDeviceError());
            verify(mockCashDrawerDevice, never()).disconnect();
            verify(mockCashDrawerDevice, never()).connect();
            verify(mockCashDrawerDevice, never()).unlock();
            return;
        }

        //assert
        fail("Expected DEVICE_BUSY, but got none");
    }

    @Test
    public void reconnect_WhenDeviceConnectFails_DoesNotReconnect() {
        //arrange
        when(mockCashDrawerDevice.tryLock()).thenReturn(true);
        when(mockCashDrawerDevice.connect()).thenReturn(false);

        //act
        try {
            cashDrawerManager.reconnectDevice();
        } catch (DeviceException deviceException) {
            assertEquals(DeviceError.DEVICE_OFFLINE, deviceException.getDeviceError());
            verify(mockCashDrawerDevice).disconnect();
            verify(mockCashDrawerDevice).connect();
            verify(mockCashDrawerDevice).unlock();
            return;
        }

        //assert
        fail("Expected DEVICE_OFFLINE, but got none");
    }

    @Test
    public void openCashDrawer_WhenLockFails_ThrowsException() {
        //arrange
        when(mockCashDrawerLock.tryLock()).thenReturn(false);

        //act
        try {
            cashDrawerManager.openCashDrawer();
        }
        //assert
        catch (DeviceException deviceException) {
            assertEquals(CashDrawerError.DEVICE_BUSY, deviceException.getDeviceError());
            return;
        }
        fail("Expected BUSY, but got none.");
    }

    @Test
    public void openCashDrawer_WhenLockSucceeds_DoesNotThrowException() throws JposException, DeviceException {
        //arrange
        when(mockCashDrawerLock.tryLock()).thenReturn(true);

        //act
        try {
            cashDrawerManager.openCashDrawer();
        }
        //assert
        catch (DeviceException deviceException) {
            fail("Lock Success should not result in Exception");
        }
        verify(mockCashDrawerDevice).openCashDrawer();
        verify(mockCashDrawerLock).unlock();
    }

    @Test
    public void openCashDrawer_WhenCashDrawerIsOffline_ThrowsJposOfflineException() throws JposException, DeviceException {
        //arrange
        when(mockCashDrawerLock.tryLock()).thenReturn(true);
        doThrow(new JposException(JposConst.JPOS_E_OFFLINE)).when(mockCashDrawerDevice).openCashDrawer();

        //act
        try {
            cashDrawerManager.openCashDrawer();
        }
        //assert
        catch (DeviceException deviceException) {
            assertEquals(DeviceError.DEVICE_OFFLINE, deviceException.getDeviceError());
            return;
        }
        fail("Expected OFFLINE Exception, but got none.");
    }

    @Test
    public void openCashDrawer_WhenCashDrawerIsOffline_ThrowsDeviceOfflineException() throws JposException, DeviceException {
        //arrange
        when(mockCashDrawerLock.tryLock()).thenReturn(true);
        doThrow(new DeviceException(DeviceError.DEVICE_OFFLINE)).when(mockCashDrawerDevice).openCashDrawer();

        //act
        try {
            cashDrawerManager.openCashDrawer();
        }
        //assert
        catch (DeviceException deviceException) {
            assertEquals(DeviceError.DEVICE_OFFLINE, deviceException.getDeviceError());
            return;
        }
        fail("Expected OFFLINE Exception, but got none.");
    }

    @Test
    public void getHealth_WhenDeviceOffline_ShouldReturnNotReadyHealthResponse() {
        //arrange
        when(mockCashDrawerDevice.isConnected()).thenReturn(false);
        when(mockCashDrawerDevice.getDeviceName()).thenReturn("cashDrawer");
        when(mockCacheManager.getCache("cashDrawerHealth")).thenReturn(testCache);
        DeviceHealthResponse expected = new DeviceHealthResponse("cashDrawer", DeviceHealth.NOTREADY);

        //act
        DeviceHealthResponse deviceHealthResponse = cashDrawerManagerCache.getHealth();

        //assert
        assertEquals("cashDrawer", deviceHealthResponse.getDeviceName());
        assertEquals(DeviceHealth.NOTREADY, deviceHealthResponse.getHealthStatus());
        assertEquals(expected.toString(), testCache.get("health_1").get().toString());
    }

    @Test
    public void getHealth_WhenDeviceOnline_ShouldReturnReadyHealthResponse() {
        //arrange
        when(mockCashDrawerDevice.isConnected()).thenReturn(true);
        when(mockCashDrawerDevice.getDeviceName()).thenReturn("cashDrawer");
        when(mockCacheManager.getCache("cashDrawerHealth")).thenReturn(testCache);
        DeviceHealthResponse expected = new DeviceHealthResponse("cashDrawer", DeviceHealth.READY);

        //act
        DeviceHealthResponse deviceHealthResponse = cashDrawerManagerCache.getHealth();

        //assert
        assertEquals("cashDrawer", deviceHealthResponse.getDeviceName());
        assertEquals(DeviceHealth.READY, deviceHealthResponse.getHealthStatus());
        assertEquals(expected.toString(), testCache.get("health_1").get().toString());
    }

    @Test
    public void getHealth_WhenCacheFails_ShouldReturnReadyHealthResponse() {
        //arrange
        when(mockCashDrawerDevice.isConnected()).thenReturn(true);
        when(mockCashDrawerDevice.getDeviceName()).thenReturn("cashDrawer");
        when(mockCacheManager.getCache("cashDrawerHealth")).thenReturn(null);
        DeviceHealthResponse expected = new DeviceHealthResponse("cashDrawer", DeviceHealth.READY);

        //act
        DeviceHealthResponse deviceHealthResponse = cashDrawerManagerCache.getHealth();

        //assert
        assertEquals("cashDrawer", deviceHealthResponse.getDeviceName());
        assertEquals(DeviceHealth.READY, deviceHealthResponse.getHealthStatus());
    }

    @Test
    public void getStatus_WhenCacheExists() {
        //arrange
        when(mockCacheManager.getCache("cashDrawerHealth")).thenReturn(testCache);
        DeviceHealthResponse expected = new DeviceHealthResponse("cashDrawer", DeviceHealth.READY);
        testCache.put("health_1", expected);

        //act
        DeviceHealthResponse deviceHealthResponse = cashDrawerManagerCache.getStatus();

        //assert
        assertEquals(expected.toString(), deviceHealthResponse.toString());
    }

    @Test
    public void getStatus_WhenCacheExists_WhenCheckHealthFlag() {
        //arrange
        when(mockCacheManager.getCache("cashDrawerHealth")).thenReturn(testCache);
        DeviceHealthResponse expected = new DeviceHealthResponse("cashDrawer", DeviceHealth.READY);
        testCache.put("health_1", expected);

        cashDrawerManagerCache.connect(); //set check health flag to CHECK_HEALTH
        when(mockCashDrawerDevice.isConnected()).thenReturn(true); //make sure health returns READY
        when(mockCashDrawerDevice.getDeviceName()).thenReturn("cashDrawer");

        //act
        DeviceHealthResponse deviceHealthResponse = cashDrawerManagerCache.getStatus();

        //assert
        assertEquals(expected.toString(), deviceHealthResponse.toString());
    }

    @Test
    public void getStatus_WhenCacheNull_WhenDeviceOffline() {
        //arrange
        when(mockCashDrawerDevice.isConnected()).thenReturn(false);
        when(mockCashDrawerDevice.getDeviceName()).thenReturn("cashDrawer");
        when(mockCacheManager.getCache("cashDrawerHealth")).thenReturn(testCache);
        DeviceHealthResponse expected = new DeviceHealthResponse("cashDrawer", DeviceHealth.NOTREADY);

        //act
        DeviceHealthResponse deviceHealthResponse = cashDrawerManagerCache.getStatus();

        //assert
        assertEquals(expected.toString(), deviceHealthResponse.toString());
    }

    @Test
    public void getStatus_WhenCacheNull_WhenDeviceOnline() {
        //arrange
        when(mockCashDrawerDevice.isConnected()).thenReturn(true);
        when(mockCashDrawerDevice.getDeviceName()).thenReturn("cashDrawer");
        when(mockCacheManager.getCache("cashDrawerHealth")).thenReturn(testCache);
        DeviceHealthResponse expected = new DeviceHealthResponse("cashDrawer", DeviceHealth.READY);

        //act
        DeviceHealthResponse deviceHealthResponse = cashDrawerManagerCache.getStatus();

        //assert
        assertEquals(expected.toString(), deviceHealthResponse.toString());
    }

    @Test
    public void getStatus_WhenCacheNull_CallHealth() {
        //arrange
        when(mockCashDrawerDevice.isConnected()).thenReturn(true);
        when(mockCashDrawerDevice.getDeviceName()).thenReturn("cashDrawer");
        when(mockCacheManager.getCache("cashDrawerHealth")).thenReturn(null);
        DeviceHealthResponse expected = new DeviceHealthResponse("cashDrawer", DeviceHealth.READY);

        //act
        DeviceHealthResponse deviceHealthResponse = cashDrawerManagerCache.getStatus();

        //assert
        assertEquals(expected.toString(), deviceHealthResponse.toString());
    }

    @Test
    public void getAllHealth_ReturnsList() {
        //arrange
        when(mockCashDrawerDevice.isConnected()).thenReturn(true);
        when(mockCashDrawerDevice.getDeviceName()).thenReturn("cashDrawer");
        when(mockCacheManager.getCache("cashDrawerHealth")).thenReturn(testCache);

        //act
        List<DeviceHealthResponse> responses = cashDrawerManagerCache.getAllHealth();

        //assert
        assertEquals(1, responses.size());
        assertEquals(DeviceHealth.READY, responses.get(0).getHealthStatus());
    }

    @Test
    public void openCashDrawer_WithDrawerId_OpensCorrectDrawer() throws JposException, DeviceException {
        //arrange
        when(mockCashDrawerLock.tryLock()).thenReturn(true);

        //act
        cashDrawerManager.openCashDrawer(1);

        //assert
        verify(mockCashDrawerDevice).openCashDrawer();
        verify(mockCashDrawerLock).unlock();
    }

    // --- Lifecycle method tests ---

    @Test
    public void openDevice_SetsManualModeAndDelegatesToDynamicDevice() throws JposException {
        //arrange

        //act
        cashDrawerManager.openDevice("CashDrawer", 1);

        //assert
        assertTrue(cashDrawerManager.isManualMode());
        verify(mockDynamicDevice).openDevice("CashDrawer");
    }

    @Test
    public void openDevice_WithInvalidDrawerId_ThrowsJposException() {
        //arrange

        //act
        try {
            cashDrawerManager.openDevice("CashDrawer", 99);
        }

        //assert
        catch (JposException jposException) {
            assertEquals(JposConst.JPOS_E_NOEXIST, jposException.getErrorCode());
            assertTrue(cashDrawerManager.isManualMode());
            return;
        }
        fail("Expected JposException, but got none.");
    }

    @Test
    public void claimDevice_DelegatesToDynamicDevice() throws JposException {
        //arrange

        //act
        cashDrawerManager.claimDevice(30000, 1);

        //assert
        assertTrue(cashDrawerManager.isManualMode());
        verify(mockDynamicDevice).claimDevice(30000);
    }

    @Test
    public void claimDevice_WithInvalidDrawerId_ThrowsJposException() {
        //arrange

        //act
        try {
            cashDrawerManager.claimDevice(30000, 99);
        }

        //assert
        catch (JposException jposException) {
            assertEquals(JposConst.JPOS_E_NOEXIST, jposException.getErrorCode());
            return;
        }
        fail("Expected JposException, but got none.");
    }

    @Test
    public void enableDevice_DelegatesToDynamicDevice() throws JposException {
        //arrange

        //act
        cashDrawerManager.enableDevice(1);

        //assert
        assertTrue(cashDrawerManager.isManualMode());
        verify(mockDynamicDevice).enableDevice();
    }

    @Test
    public void enableDevice_WithInvalidDrawerId_ThrowsJposException() {
        //arrange

        //act
        try {
            cashDrawerManager.enableDevice(99);
        }

        //assert
        catch (JposException jposException) {
            assertEquals(JposConst.JPOS_E_NOEXIST, jposException.getErrorCode());
            return;
        }
        fail("Expected JposException, but got none.");
    }

    @Test
    public void disableDevice_DelegatesToDynamicDevice() throws JposException {
        //arrange

        //act
        cashDrawerManager.disableDevice(1);

        //assert
        assertTrue(cashDrawerManager.isManualMode());
        verify(mockDynamicDevice).disableDevice();
    }

    @Test
    public void disableDevice_WithInvalidDrawerId_ThrowsJposException() {
        //arrange

        //act
        try {
            cashDrawerManager.disableDevice(99);
        }

        //assert
        catch (JposException jposException) {
            assertEquals(JposConst.JPOS_E_NOEXIST, jposException.getErrorCode());
            return;
        }
        fail("Expected JposException, but got none.");
    }

    @Test
    public void releaseDevice_DelegatesToDynamicDevice() throws JposException {
        //arrange

        //act
        cashDrawerManager.releaseDevice(1);

        //assert
        assertTrue(cashDrawerManager.isManualMode());
        verify(mockDynamicDevice).releaseDevice();
    }

    @Test
    public void releaseDevice_WithInvalidDrawerId_ThrowsJposException() {
        //arrange

        //act
        try {
            cashDrawerManager.releaseDevice(99);
        }

        //assert
        catch (JposException jposException) {
            assertEquals(JposConst.JPOS_E_NOEXIST, jposException.getErrorCode());
            return;
        }
        fail("Expected JposException, but got none.");
    }

    @Test
    public void closeDevice_DelegatesToDynamicDevice() throws JposException {
        //arrange

        //act
        cashDrawerManager.closeDevice(1);

        //assert
        assertTrue(cashDrawerManager.isManualMode());
        verify(mockDynamicDevice).closeDevice();
    }

    @Test
    public void closeDevice_WithInvalidDrawerId_ThrowsJposException() {
        //arrange

        //act
        try {
            cashDrawerManager.closeDevice(99);
        }

        //assert
        catch (JposException jposException) {
            assertEquals(JposConst.JPOS_E_NOEXIST, jposException.getErrorCode());
            return;
        }
        fail("Expected JposException, but got none.");
    }

    @Test
    public void setAutoMode_SetsManualModeToFalse() throws JposException {
        //arrange
        cashDrawerManager.openDevice("CashDrawer", 1); // sets manualMode = true
        assertTrue(cashDrawerManager.isManualMode());

        //act
        cashDrawerManager.setAutoMode();

        //assert
        assertFalse(cashDrawerManager.isManualMode());
    }

    @Test
    public void getLifecycleStatus_ReturnsStatusForAllDrawers() {
        //arrange
        when(mockDynamicDevice.getLifecycleState()).thenReturn(DeviceLifecycleState.CLOSED);
        when(mockCashDrawerDevice.getDeviceName()).thenReturn("cashDrawer");

        //act
        List<DeviceLifecycleResponse> responses = cashDrawerManager.getLifecycleStatus();

        //assert
        assertEquals(1, responses.size());
        assertEquals(DeviceLifecycleState.CLOSED, responses.get(0).getState());
        assertEquals("cashDrawer", responses.get(0).getLogicalName());
        assertEquals("CashDrawer/1", responses.get(0).getDeviceType());
    }

    @Test
    public void connect_WhenManualModeTrue_SkipsConnection() throws JposException {
        //arrange
        cashDrawerManager.openDevice("CashDrawer", 1); // sets manualMode = true
        reset(mockCashDrawerDevice);
        when(mockCashDrawerDevice.getDrawerId()).thenReturn(1);
        when(mockCashDrawerDevice.getDynamicDevice()).thenReturn(mockDynamicDevice);

        //act
        cashDrawerManager.connect();

        //assert
        verify(mockCashDrawerDevice, never()).tryLock();
        verify(mockCashDrawerDevice, never()).connect();
    }

    @Test
    public void isManualMode_DefaultIsFalse() {
        //arrange

        //act
        boolean result = cashDrawerManager.isManualMode();

        //assert
        assertFalse(result);
    }

    @Test
    public void isManualMode_AfterLifecycleCall_IsTrue() throws JposException {
        //arrange
        cashDrawerManager.openDevice("CashDrawer", 1);

        //act
        boolean result = cashDrawerManager.isManualMode();

        //assert
        assertTrue(result);
    }

    @Test
    public void getLifecycleStatus_ReflectsManualMode() throws JposException {
        //arrange
        when(mockDynamicDevice.getLifecycleState()).thenReturn(DeviceLifecycleState.ENABLED);
        when(mockCashDrawerDevice.getDeviceName()).thenReturn("cashDrawer");

        //act -- before manual mode
        List<DeviceLifecycleResponse> beforeManual = cashDrawerManager.getLifecycleStatus();

        //assert
        assertFalse(beforeManual.get(0).isManualMode());

        //act -- after lifecycle call sets manual mode
        cashDrawerManager.enableDevice(1);
        List<DeviceLifecycleResponse> afterManual = cashDrawerManager.getLifecycleStatus();

        //assert
        assertTrue(afterManual.get(0).isManualMode());
    }
}
