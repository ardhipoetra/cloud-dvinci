package nl.tudelft.in4392.model;

import org.opennebula.client.Client;
import org.opennebula.client.vm.VirtualMachine;

import java.io.Serializable;

/**
 * Created by ardhipoetra on 10/20/15.
 */
public class VinciVM extends VirtualMachine implements Serializable{

    private static final long serialVersionUID = 8205049349060267905L;

    public static final int VM_ON = 1;
    public static final int VM_OFF = 0;
    public static final int VM_UNKNOWN = -1;


    public int id;
    public String ip;
    public double mem;
    public double cpu;
    public int status = VM_UNKNOWN;
    public long bornTime = -1;

    public String xmlData;
    public String hostname;
    public int runningJobs;

    // from XML
    public VinciVM(int vmid, Client c, String vmip, double vmmem, double vmcpu) {
        super(vmid, c);
        this.id = vmid;
        this.ip = vmip;
        this.mem = vmmem;
        this.cpu = vmcpu;

        if (bornTime == -1) bornTime = System.currentTimeMillis();
    }

    // from createNew
    public VinciVM(int vmid, Client c) {
        super(vmid, c);
        this.mem = Double.MAX_VALUE;
        this.cpu = Double.MAX_VALUE;

        bornTime = System.currentTimeMillis();
    }


    public String checkMemComm() {
        String command1 = "free|grep Mem|awk '{print $4/$2*100.0}'";
        return command1;
    }


    public String checkCpuComm() {
        String command1 = "top -b -d1 -n1|grep -i 'Cpu(s)'|head -c21|cut -d ' ' -f3|cut -d '%' -f1";
        return command1;
    }


    public void setXmlData(String s) {this.xmlData = s;}

    @Override
    public boolean equals(Object obj) {
        if(obj instanceof VinciVM) {
            return this.id == ((VinciVM) obj).id;
        }
        return false;
    }

    @Override
    public int hashCode() {
        return id;
    }

    public void setHostname(String hostname) {
        this.hostname = hostname;
    }
}
