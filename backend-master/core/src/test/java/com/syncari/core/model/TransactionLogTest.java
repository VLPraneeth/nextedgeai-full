package com.syncari.core.model;

import com.syncari.connector.Operation;
import com.syncari.core.model.misc.ExternalValue;
import org.junit.Test;

import static junit.framework.TestCase.assertFalse;
import static junit.framework.TestCase.assertTrue;

public class TransactionLogTest {

    @Test
    public void hasSameChangeTest(){
        FieldChange change = new FieldChange().setFieldId("field1").setApiName("Field 1")
                .setSrcId("connector1").setOldValue("Old_1").setNewValue("New_1")
                .addIncomingExternalValue("externalField1", new ExternalValue().setValue("externalValue2"))
                .addIncomingExternalValue("externalField2", new ExternalValue().setValue("externalValue3"))
                .setTimestamp(123456l);

        FieldChange changeCopy = new FieldChange().setFieldId("field1").setApiName("Field 1")
                .setSrcId("connector1").setOldValue("Old_1").setNewValue("New_1")
                .addIncomingExternalValue("externalField1", new ExternalValue().setValue("externalValue2"))
                .addIncomingExternalValue("externalField2", new ExternalValue().setValue("externalValue3"))
                .setTimestamp(99999l);
        FieldChange differentChange = new FieldChange().setFieldId("field1").setApiName("Field 1")
                .setSrcId("connector1").setOldValue("Old_2").setNewValue("New_1")
                .addIncomingExternalValue("externalField1", new ExternalValue().setValue("externalValue2"))
                .setTimestamp(123456l);
        var log2 =new TransactionLog().addChange(change)
                .setOperation(Operation.update)
                .setSyncariId("syncariId1");
        assertTrue(log2.hasSameChange(changeCopy));
        assertFalse(log2.hasSameChange(differentChange));
    }

    @Test
    public void newTransactionLogHasDefaultOccuredAtAsNow(){
        TransactionLog txnLog = new TransactionLog();
        // Default transaction log has the system time.
        assertTrue(txnLog.getOccurredAt() > 0);
        long origOccuredAt = txnLog.getOccurredAt();
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            // do nothing
        }
        // Callers can override as needed
        txnLog.setOccurredAt(System.currentTimeMillis());
        assertTrue(txnLog.getOccurredAt() > origOccuredAt);
    }
}
