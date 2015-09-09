package flexible_architecture.net;

import java.util.ArrayList;
import java.util.List;

import flexible_architecture.block.AbstractBlock;

public abstract class AbstractNet {
	
	private AbstractBlock source;
	
	public AbstractNet(AbstractBlock source) {
		this.source = source;
	}
	
	public AbstractBlock getSource() {
		return this.source;
	}
	public void setSource(AbstractBlock source) {
		this.source = source;
	}
}