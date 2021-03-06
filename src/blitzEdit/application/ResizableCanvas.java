package blitzEdit.application;

import javafx.scene.canvas.Canvas;

public class ResizableCanvas extends Canvas
{	
	@Override
	public double minHeight(double width)
	{
	    return 200;
	}

	@Override
	public double maxHeight(double width)
	{
	    return 2000;
	}

	@Override
	public double prefHeight(double width)
	{
	    return minHeight(width);
	}

	@Override
	public double minWidth(double height)
	{
	    return 200;
	}

	@Override
	public double maxWidth(double height)
	{
	    return 2000;
	}
	
	@Override
	public double prefWidth(double height)
	{
	    return minHeight(height);
	}

	@Override
	public boolean isResizable()
	{
	    return true;
	}

	@Override
	public void resize(double width, double height)
	{
	    super.setWidth(width);
	    super.setHeight(height);
	}
}
