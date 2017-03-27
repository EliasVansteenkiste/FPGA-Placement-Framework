package place.placers.analytical;

import java.util.List;
import java.util.Map;

import place.circuit.Circuit;
import place.circuit.architecture.BlockCategory;
import place.circuit.architecture.BlockType;
import place.circuit.block.GlobalBlock;
import place.placers.analytical.AnalyticalAndGradientPlacer.Net;
import place.placers.analytical.AnalyticalAndGradientPlacer.NetBlock;
import place.visual.PlacementVisualizer;

abstract class Legalizer {

    protected Circuit circuit;
    protected int width, height;

    private List<BlockType> blockTypes;
    private List<Integer> blockTypeIndexStarts;
    private int numBlocks, numIOBlocks;

    protected double tileCapacity;

    protected double[] linearX, linearY;
    protected int[] legalX, legalY;
    protected int[] heights;

    // Properties of the blockType that is currently being legalized
    protected BlockType blockType;
    protected BlockCategory blockCategory;
    protected int blockStart, blockRepeat, blockHeight;
    
    //Hard Block Legalizer
    private HardblockConnectionLegalizer hardblockLegalizer;
    
    //Visualizer
    private PlacementVisualizer visualizer;
    private final Map<GlobalBlock, NetBlock> netBlocks;

    Legalizer(
            Circuit circuit,
            List<BlockType> blockTypes,
            List<Integer> blockTypeIndexStarts,
            double[] linearX,
            double[] linearY,
            int[] legalX,
            int[] legalY,
            int[] heights,
            PlacementVisualizer visualizer,
            List<Net> nets,
            Map<GlobalBlock, NetBlock> netBlocks) {

        // Store easy stuff
        this.circuit = circuit;
        this.width = this.circuit.getWidth();
        this.height = this.circuit.getHeight();

        // Store block types
        if(blockTypes.get(0).getCategory() != BlockCategory.IO) {
            throw new IllegalArgumentException("The first block type is not IO");
        }
        if(blockTypes.size() != blockTypeIndexStarts.size() - 1) {
            throw new IllegalArgumentException("The objects blockTypes and blockTypeIndexStarts don't have matching dimensions");
        }

        this.blockTypes = blockTypes;
        this.blockTypeIndexStarts = blockTypeIndexStarts;

        // Store linear solution (this array is updated by the linear solver
        this.linearX = linearX;
        this.linearY = linearY;
        this.heights = heights;

        // Cache the number of blocks
        this.numBlocks = linearX.length;
        this.numIOBlocks = blockTypeIndexStarts.get(1);

        // Initialize the solution with IO positions
        this.legalX = new int[this.numBlocks];
        this.legalY = new int[this.numBlocks];

        System.arraycopy(legalX, 0, this.legalX, 0, this.numBlocks);
        System.arraycopy(legalY, 0, this.legalY, 0, this.numBlocks);

        //Hard block legalizer
        this.netBlocks = netBlocks;
        this.hardblockLegalizer = new HardblockConnectionLegalizer(this.linearX, this.linearY, this.legalX, this.legalY, this.heights, this.width, this.height, nets, netBlocks);

        // Information to visualize the legalisation progress
        this.visualizer = visualizer;
    }

    Legalizer(Legalizer legalizer) {
        this.circuit = legalizer.circuit;
        this.width = legalizer.width;
        this.height = legalizer.height;

        this.blockTypes = legalizer.blockTypes;
        this.blockTypeIndexStarts = legalizer.blockTypeIndexStarts;

        this.linearX = legalizer.linearX;
        this.linearY = legalizer.linearY;
        this.legalX = legalizer.legalX;
        this.legalY = legalizer.legalY;
        this.heights = legalizer.heights;

        this.numBlocks = legalizer.numBlocks;
        this.numIOBlocks = legalizer.numIOBlocks;
        
        this.visualizer = legalizer.visualizer;
        this.netBlocks = legalizer.netBlocks;
    }

    protected abstract void legalizeBlockType(int blocksStart, int blocksEnd);

    void legalize(double tileCapacity) {
        this.tileCapacity = tileCapacity;

        // Skip i = 0: these are IO blocks
        for(int i = 1; i < this.blockTypes.size(); i++) {
            this.blockType = this.blockTypes.get(i);
            this.legalizeBlockType(i);
        }
    }
    
    void legalize(double tileCapacity, BlockType movableBlockType) {
        this.tileCapacity = tileCapacity;

        // Skip i = 0: these are IO blocks
        for(int i = 1; i < this.blockTypes.size(); i++) {
            this.blockType = this.blockTypes.get(i);
            if(movableBlockType.equals(this.blockType)){
            	this.legalizeBlockType(i);
            }
        }
    }
    
    private void legalizeBlockType(int i){
        int blocksStart = this.blockTypeIndexStarts.get(i);
        int blocksEnd = this.blockTypeIndexStarts.get(i + 1);

        if(blocksEnd > blocksStart) {
            this.blockCategory = this.blockType.getCategory();

            this.blockStart = Math.max(1, this.blockType.getStart());
            this.blockHeight = this.blockType.getHeight();
            this.blockRepeat = this.blockType.getRepeat();
            if(this.blockRepeat == -1) {
                this.blockRepeat = this.width;
            }

            long start = System.nanoTime();
           
            if(this.blockType.getCategory().equals(BlockCategory.CLB)){
            	this.legalizeBlockType(blocksStart, blocksEnd);
        	}else if(this.blockType.getCategory().equals(BlockCategory.HARDBLOCK)){
        		this.hardblockLegalizer.legalizeBlockType(blocksStart, blocksEnd, this.blockStart, this.blockRepeat, this.blockHeight);
        	}else{
        		System.out.println("unrecognized block type: " + this.blockType);
        	}
           
            long end = System.nanoTime();
    		double time = (end -  start) * Math.pow(10, -6);
        	System.out.printf("%.0f ms\n", time);
        }
    }

    int[] getLegalX() {
        return this.legalX;
    }
    int[] getLegalY() {
        return this.legalY;
    }
    
    protected void addVisual(String name, double[] linearX, double[] linearY){
    	this.visualizer.addPlacement(name, this.netBlocks, linearX, linearY, -1);
    }
    protected void addVisual(String name, int[] linearX, int[] linearY){
    	this.visualizer.addPlacement(name, this.netBlocks, linearX, linearY, -1);
    }
}
