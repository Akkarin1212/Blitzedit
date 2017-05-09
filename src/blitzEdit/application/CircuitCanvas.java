package blitzEdit.application;

import java.util.ArrayList;

import blitzEdit.core.Circuit;
import blitzEdit.core.Component;
import blitzEdit.core.Element;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.MenuItem;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.ScrollPane.ScrollBarPolicy;
import javafx.scene.input.DragEvent;
import javafx.scene.input.MouseDragEvent;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.AnchorPane;

public class CircuitCanvas extends ResizableCanvas
{
	GraphicsContext gc;
	Circuit circuit;
	private CircuitCanvas ref = this;
	private ContextMenu rightClickMenu;
	
	private String currentSvgPath;
	private Element currentMovingElement;
	
	
	public CircuitCanvas(ScrollPane sp)
	{
		gc = getGraphicsContext2D();
		circuit = new Circuit();
		
		currentSvgPath = "img/Widerstand.svg";
		
		 sp.setPannable(true);
		
		onDragDetectedHandler();
		onMouseDragOverHandler();
		onMouseDragReleasedHandler();
		
		
		onClickHandler();
		//initiateRightClickMenu();
	}
	
	private void onClickHandler()
	{
		EventHandler<MouseEvent> OnMousePressedEventHandler = new EventHandler<MouseEvent>() {
			@Override
			public void handle(MouseEvent click)
			{
				if(click.isSecondaryButtonDown())
				{
				int [][] relPos = {{0, 10},{0, -10}};
				Component comp = new Component((int)click.getX(), (int)click.getY(), (short)0, "dunno", relPos, currentSvgPath);
				circuit.addElement(comp);
				refreshCanvas();
				}
			}
		};
		this.setOnMousePressed(OnMousePressedEventHandler);
	}
	
	private void onDragDetectedHandler()
	{
		this.setOnDragDetected(new EventHandler<MouseEvent>() {
		    @Override 
		    public void handle(MouseEvent event) {
		    	System.err.println((int)event.getX() + " " + (int)event.getY());
		        ArrayList<Element> elements = circuit.getElementsByPosition((int)event.getX(), (int)event.getY());
		        if(elements != null)
		        {
		        	currentMovingElement = elements.get(0); //take first element found
		        }
		        ref.startFullDrag();
		    }
		});
	}
	
	private void onMouseDragOverHandler()
	{
		if(true)//(currentMovingElement != null)
		{
			this.setOnMouseDragOver(new EventHandler<MouseDragEvent>() {
			@Override 
		    public void handle(MouseDragEvent event) 
		    {
				System.err.println((int)event.getX() + " " + (int)event.getY());
				//currentMovingElement.move((int)event.getX(), (int)event.getY());
		    }
			});
		}
	}
	
	private void onMouseDragEnteredHandler()
	{

	}
	
	private void onMouseDragReleasedHandler()
	{
		if(currentMovingElement != null)
		{
			this.setOnMouseDragReleased(new EventHandler<MouseDragEvent>() {
			@Override 
		    public void handle(MouseDragEvent event) 
		    {
		       currentMovingElement = null;
		    }
			});
		}
	}
	
	
	public void drawGrid()
	{
        gc.clearRect(0, 0, getWidth(), getHeight());

        gc.setLineWidth(0.1);
        
        // vertical lines
        for(int i = 0 ; i < getWidth() ; i+=30){
            gc.strokeLine(i, 0, i, getHeight());
        }        

        // horizontal lines
        for(int i = 30 ; i < getHeight() ; i+=30){
            gc.strokeLine(30, i, getWidth(), i);
        } 
	}
	
	public void refreshCanvas()
	{
		drawGrid();
	    drawAllCircuitElements();
	}
	
	private void drawAllCircuitElements()
	{
		ArrayList<Element> array = circuit.getElements();
		for(Element elem : array)
		{
			elem.draw(gc, 1.0);
		}
	}
	
	private void initiateRightClickMenu()
	{
		final ContextMenu contextMenu = new ContextMenu();
		MenuItem cut = new MenuItem("Cut");
		MenuItem copy = new MenuItem("Copy");
		MenuItem paste = new MenuItem("Paste");
		
		EventHandler<ActionEvent> OnMouseClickedEventHandler = new EventHandler<ActionEvent>() {
			@Override
			public void handle(ActionEvent click)
			{
				System.out.println("cut");
			}
		};
		cut.setOnAction(OnMouseClickedEventHandler);
		
		contextMenu.getItems().addAll(cut, copy, paste);
		
		CircuitCanvas ref = this;
		setOnMousePressed(new EventHandler<MouseEvent>() {
		    @Override
		    public void handle(MouseEvent event) {
		        if (event.isSecondaryButtonDown()) {
		            contextMenu.show(ref, event.getScreenX(), event.getScreenY());
		        }
		    }
		});
	}
	
	@Override
	public void resize(double width, double height)
	{
	    super.setWidth(width);
	    super.setHeight(height);
	    refreshCanvas();
	}
	
	@Override
	public double minWidth(double height)
	{
	    return 2000;
	}

	@Override
	public double maxWidth(double height)
	{
	    return 4000;
	}
	
	@Override
	public double minHeight(double width)
	{
	    return 2000;
	}

	@Override
	public double maxHeight(double width)
	{
	    return 4000;
	}
}
