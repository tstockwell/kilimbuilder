package kilim.tools;

import kilim.analysis.BBList;
import kilim.analysis.BasicBlock;
import kilim.analysis.MethodFlow;

public class DumpUtils {
	public static void DumpBasicBlockStructure(MethodFlow methodFlow) {
		System.out.println("MethodFlow("+methodFlow.hashCode()+") { ");
		System.out.println("\t\thashcode:"+methodFlow.hashCode());
		System.out.println("\tbasicBlocks: {");
		BBList bbList= methodFlow.getBasicBlocks();
		for (BasicBlock basicBlock:bbList) {
			System.out.println("\t\tBasicBlock { ");
			System.out.println("\t\t\thashcode:"+basicBlock.hashCode());
			System.out.println("\t\t\tstartPos:"+basicBlock.startPos);
			System.out.println("\t\t\tendPos:"+basicBlock.endPos);
			System.out.println("\t\t\tsuccessors: {");
			for (BasicBlock successor:basicBlock.successors) {
				System.out.println("\t\t\t\t"+successor.hashCode());
			}
			System.out.println("\t\t\t}");
			System.out.println("\t\t}");
		}
		System.out.println("\t}");
		System.out.println("}");
	}
}
