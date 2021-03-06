package nl.tudelft.in4392.manager;

import nl.tudelft.in4392.Constants;
import nl.tudelft.in4392.Utility;
import nl.tudelft.in4392.model.VinciVM;
import org.opennebula.client.Client;
import org.opennebula.client.ClientConfigurationException;
import org.opennebula.client.OneResponse;
import org.opennebula.client.vm.VirtualMachine;
import org.opennebula.client.vm.VirtualMachinePool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;
import sun.management.VMManagement;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.*;
import java.nio.charset.Charset;
import java.util.*;
import java.net.InetAddress;

public class VMmanager {

    public static Client c;

    public static HashMap<Integer, VinciVM> vmList = new HashMap<Integer, VinciVM>();

    static Logger logger = LoggerFactory.getLogger(VMmanager.class);

    static int currentTargetVm = -1;

    static boolean creatingVMStatus = false;

    public static VinciVM createVM() throws Exception{

        FileInputStream fileInputStream = new FileInputStream(Constants.VM_TEMPLATE_DEFAULT);
        byte[] buffer = new byte[fileInputStream.available()];
        int length = fileInputStream.read(buffer);
        fileInputStream.close();

        String vmtemplate = new String(buffer, 0, length, Charset.forName("UTF-8"));

        try {
            if (c == null)
                c = new Client(Constants.CREDENTIALS, null);
        } catch (ClientConfigurationException e) {
            e.printStackTrace();
        }

        OneResponse rc = VirtualMachine.allocate(c, vmtemplate);

        //logger.info("data : " + length);

        if(rc.isError()) {
            logger.error("failed");
            throw new Exception( rc.getErrorMessage() );
        }

        int newVMID = Integer.parseInt(rc.getMessage());
        System.out.println("ok, ID " + newVMID + ".");

        VinciVM vm = new VinciVM(newVMID, c);
        rc = vm.info();

        if(rc.isError())
            throw new Exception( rc.getErrorMessage() );

        vm = VMmanager.parseXML(rc.getMessage());
        vm.status = VinciVM.VM_OFF;
        logger.info("VM "+vm.id()+" created"); //hedi
        System.out.println("The new VM " + vm.getName() + " has status: " + vm.status() + ". Now initialize");
        return vm;
    }

    public static VinciVM getVM(int id) {
        return vmList.get(id);
    }

    public static OneResponse deleteVM(VinciVM vm) {
        vmList.remove(vm.id());
        OneResponse or = vm.finalizeVM();
	logger.info("VM " + vm.id() + " deleted"); //hedi
        return or;
    }

    public static String showVMInfo(VinciVM vm) {
        StringBuilder sb = new StringBuilder();
        sb.append("ID : ");
        sb.append(vm.id());
        sb.append('\n');
        sb.append(vm.info().getMessage());

        return sb.toString();
    }

    public static String showAllVms() throws Exception {
        try {
            if (c == null)
                c = new Client(Constants.CREDENTIALS, null);
        } catch (ClientConfigurationException e) {
            e.printStackTrace();
        }

        VirtualMachinePool vpool = new VirtualMachinePool(c);

        String allXmlVm = vpool.infoAll().getMessage();

        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        DocumentBuilder builder = factory.newDocumentBuilder();

        Document document = builder.parse(new InputSource(new StringReader(allXmlVm)));
        document.getDocumentElement().normalize();

        NodeList nList = document.getElementsByTagName("VM");

        String ret = "";

        for (int temp = 0; temp < nList.getLength(); temp++) {
            Node node = nList.item(temp);
            if (node.getNodeType() == Node.ELEMENT_NODE) {
                Element eElement = (Element) node;

                VinciVM vmTmp = parseXML(Utility.nodeToString(eElement));

                ret += "VM ("+vmTmp.id+") IP : "+vmTmp.ip;
                ret += "\n";
            }
        }

        return ret;
    }

    public static VinciVM getAvailableVM() {

        while(VMmanager.currentTargetVm < 0) {
	}
//            int numberOn = 0; int numberOff = 0;
//            Iterator<Map.Entry<Integer, VinciVM>> it = vmList.entrySet().iterator();
//            while (it.hasNext()) {
//                Map.Entry<Integer, VinciVM> kv = it.next();
//
//                if(kv.getValue().status == VinciVM.VM_ON) numberOn++;
//                else if(kv.getValue().status == VinciVM.VM_OFF) numberOff++;
//            }
//	         logger.info("ON:"+numberOn+" OFF:"+numberOff);


        return vmList.get(VMmanager.currentTargetVm);
    }


    public static VinciVM parseXML(String s) throws  Exception{

        try {
            if (c == null)
                c = new Client(Constants.CREDENTIALS, null);
        } catch (ClientConfigurationException e) {
            e.printStackTrace();
        }

        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        DocumentBuilder builder = factory.newDocumentBuilder();

        //Build Document
        Document document = builder.parse(new InputSource(new StringReader(s)));

        //Normalize the XML Structure; It's just too important !!
        document.getDocumentElement().normalize();

        //Here comes the root node
        Element eElement = document.getDocumentElement();

        VinciVM ret = new VinciVM(Integer.parseInt(eElement.getElementsByTagName("ID").item(0).getTextContent()),
                c,eElement.getElementsByTagName("IP").item(0).getTextContent(),
                Double.MAX_VALUE,
                Double.MAX_VALUE);

        try {
            ret.setHostname(eElement.getElementsByTagName("HOSTNAME").item(1).getTextContent());
            ret.status = VinciVM.VM_ON;
        } catch (NullPointerException ne) {
            ret.status = VinciVM.VM_OFF;
        }

        ret.setXmlData(s);

        if(!vmList.containsKey(ret.id())) {
            ret.bornTime = System.currentTimeMillis();
        }

        vmList.put(ret.id, ret);

        System.out.println("parsed : "+ret.id);
        return ret;
    }

    public static void startVMmonitor() {
        Thread thread = new Thread() {
            public void run() {
                while (true){

                Iterator<Map.Entry<Integer, VinciVM>> it = vmList.entrySet().iterator();

                creatingVMStatus = false;
                int totalRunningJob = 0;
                while(it.hasNext()) {
                    Map.Entry<Integer, VinciVM> kv = it.next();

                    try {
                        //get log file. format: vm_id.log
                        FileInputStream in = new FileInputStream("/home/cld1593/log/"+kv.getKey()+".log");
                        BufferedReader br = new BufferedReader(new InputStreamReader(in));
                        String strLine = null, tmp;
                        while ((tmp = br.readLine()) != null) strLine = tmp;

                        //read last line, split the mem and proc util, put into array
                        String lastLine = strLine;

                        in.close();

                        String[] sarray = strLine.split(" ");
                        VinciVM vm = kv.getValue();

                        System.out.print("V" + vm.id() + ":" + vm.runningJobs + " ");

                        vm.mem = Double.parseDouble(sarray[0]);
                        vm.cpu = Double.parseDouble(sarray[1]);
                        logger.info("VM_util " + kv.getKey() + " " + vm.mem + " " + vm.cpu); //hedi
                        logger.info("V" + vm.id() + ":" + vm.runningJobs + " "); //hedii
                        totalRunningJob += vm.runningJobs;



                    } catch (FileNotFoundException e) {
                        System.out.println("\nfile "+kv.getKey()+" not found, that means the util has not running");
                        creatingVMStatus = true;

                        try {
                            // check new XML
                            VinciVM errVm = VMmanager.parseXML(kv.getValue().info().getMessage());

                            // if the status ON, send initialization
                            if (errVm.status == VinciVM.VM_ON) {
                                InetAddress address = InetAddress.getByName(errVm.hostname);
                                boolean runCheck = false;

                                while(true) {
                                    if (address.isReachable(1000)) {
                                        Runtime.getRuntime().exec("ssh " + errVm.hostname + " sh /home/cld1593/log/util.sh " + errVm.id());
                                        System.out.println("initialization~");
                                        break;
                                    }
                                }
                            }

                        } catch (Exception ex){}
                    } catch (Exception e) { // let it go~
                    }
                }
                    System.out.println();

                Double minMem = Double.MAX_VALUE; int idMinMemVm = -1;
                Double minCpu = Double.MAX_VALUE; int idMinCpuVm = -1;

                int minRun = Integer.MAX_VALUE; int minRunId = -1;

                Iterator<Map.Entry<Integer, VinciVM>> itCheck = vmList.entrySet().iterator();
                while (itCheck.hasNext()) {
                    Map.Entry<Integer,VinciVM> e = itCheck.next();
                    if(minMem.compareTo(e.getValue().mem) > 0) {
                        idMinMemVm = e.getKey();
                        minMem = e.getValue().mem;
                    }

                    if(minCpu.compareTo(e.getValue().cpu) > 0) {
                        idMinCpuVm = e.getKey();
                        minCpu = e.getValue().cpu;
                    }

                    if(minRun > e.getValue().runningJobs) {
                        minRun = e.getValue().runningJobs;
                        minRunId = e.getKey();
                    }
                }

                int average = totalRunningJob/vmList.size();

//                    System.out.println(average + " - "+minRun+" "+minRunId);
                if(minRun > average)
                    VMmanager.currentTargetVm = idMinCpuVm;
                else
                    VMmanager.currentTargetVm = minRunId;

                System.out.printf("MEM (%f) : %d | CPU (%f) : %d | %d \n", minMem, idMinMemVm, minCpu, idMinCpuVm, VMmanager.currentTargetVm);


                int numberOn = 0; int numberOff = 0;
                StringBuilder sb = new StringBuilder();
                Iterator<Map.Entry<Integer, VinciVM>> iter = vmList.entrySet().iterator();
                while (iter.hasNext()) {
                    Map.Entry<Integer, VinciVM> kva = iter.next();

                    sb.append(kva.getKey());
                    if(kva.getValue().status == VinciVM.VM_ON)
                        numberOn++;
                    else if(kva.getValue().status == VinciVM.VM_OFF) {
                        numberOff++;
                        sb.append('!');
                    }
                    sb.append(' ');
                }
                logger.info(sb.toString());
                logger.info("ON:" + numberOn + " OFF:" + numberOff);

//                logger.info("Total job running :" +totalRunningJob);
                //if least utilized VM is less than 70%, create new VM
                if (minCpu.compareTo(new Double("70")) > 0 && !creatingVMStatus) {
                    System.out.println("create new VM");

                    try {
                        VinciVM newvm = VMmanager.createVM();
                        System.out.println("created VM : "+newvm.id());
                        VMmanager.currentTargetVm = newvm.id();
                    } catch (Exception e) {e.printStackTrace();}
                }
                // if the least utilized is less than 20% and the size is large enough (3), delete it, pick the rest randomly
                else if (minCpu.compareTo(new Double("20")) < 0 && vmList.size() > 3) {
                    VinciVM vmVictim = vmList.get(VMmanager.currentTargetVm);

                    if (System.currentTimeMillis() - vmVictim.bornTime > 1000 * Constants.VM_KILL_TIMEOUT) {
                        System.out.println("VM" +VMmanager.currentTargetVm +" utlization is less than 20%, remove it from VM pool");

                        VMmanager.deleteVM(vmVictim);
                        VMmanager.currentTargetVm = new ArrayList<Integer>(vmList.keySet())
                                .get(new Random().nextInt(vmList.keySet().size()));
                    } else {
                        System.out.println("VM" +VMmanager.currentTargetVm +" still fresh baby, not delete it "+(System.currentTimeMillis() - vmVictim.bornTime));
                    }

                }


                // interval check
                try {
                    Thread.sleep(Constants.VM_MONITOR_CHECK_INTERVAL);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
            }
        };
        thread.start();
    }
}
