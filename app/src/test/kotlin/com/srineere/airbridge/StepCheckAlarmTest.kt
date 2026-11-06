package com.srineere.airbridge

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The matrix behind the step check's alarm.
 *
 * It is worth a test of its own because getting it wrong is invisible on
 * the one phone it was written on: a device whose user has already allowed
 * the battery exemption answers yes down every path, and the case that
 * actually breaks -- API 33 with neither the exemption nor "Alarms &
 * reminders" -- is somebody else's phone throwing SecurityException out of
 * a settings change.
 */
class StepCheckAlarmTest {

    @Test fun `below API 31 there is no permission to ask about`() {
        // minSdk is 28, and canScheduleExactAlarms does not exist on those
        // versions: the service passes false for it rather than calling a
        // method that is not there.
        assertTrue(canArmExactAlarm(28, systemAllowsExact = false, batteryExempt = false))
        assertTrue(canArmExactAlarm(30, systemAllowsExact = false, batteryExempt = false))
    }

    @Test fun `from API 31 the platform's own answer is enough`() {
        assertTrue(canArmExactAlarm(31, systemAllowsExact = true, batteryExempt = false))
        assertTrue(canArmExactAlarm(36, systemAllowsExact = true, batteryExempt = false))
    }

    @Test fun `the battery exemption is a second yes`() {
        // The exemption carries the exact-alarm right with it, so this
        // ought to be unreachable; it is asked anyway because the two have
        // been seen to disagree, and a wrong yes only costs a caught
        // exception.
        assertTrue(canArmExactAlarm(34, systemAllowsExact = false, batteryExempt = true))
    }

    @Test fun `with neither right, the caller is told to use the handler`() {
        assertFalse(canArmExactAlarm(31, systemAllowsExact = false, batteryExempt = false))
        assertFalse(canArmExactAlarm(33, systemAllowsExact = false, batteryExempt = false))
        assertFalse(canArmExactAlarm(36, systemAllowsExact = false, batteryExempt = false))
    }
}
