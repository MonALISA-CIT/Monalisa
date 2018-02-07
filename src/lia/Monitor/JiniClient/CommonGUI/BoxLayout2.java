package lia.Monitor.JiniClient.CommonGUI;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.LayoutManager2;
import java.io.Serializable;

import javax.swing.BoxLayout;

public class BoxLayout2
    implements LayoutManager2, Serializable
{

    BoxLayout layout = null;
    int axis = 0;

    public BoxLayout2()
    {
        axis = 0;
    }

    public BoxLayout2(Container parent, int axis)
    {
        this.axis = 0;
        this.axis = axis;
        layout = new BoxLayout(parent, axis);
    }

    public int getAxis()
    {
        return axis;
    }

    public void setAxis(int axis)
    {
        if (axis != this.axis)
        {
            layout = null;
            this.axis = axis;
        }
    }

    void verifyInstance(Container parent)
    {
        if (layout == null)
        {
            layout = new BoxLayout(parent, axis);
        }
    }

    public void addLayoutComponent(Component component, Object constraint)
    {
        try
        {
            component.getParent().invalidate();
        }
        catch (Exception exception) { }
    }

    public Dimension maximumLayoutSize(Container parent)
    {
        verifyInstance(parent);
        return layout.maximumLayoutSize(parent);
    }

    public float getLayoutAlignmentX(Container parent)
    {
        verifyInstance(parent);
        return layout.getLayoutAlignmentX(parent);
    }

    public float getLayoutAlignmentY(Container parent)
    {
        verifyInstance(parent);
        return layout.getLayoutAlignmentY(parent);
    }

    public void invalidateLayout(Container parent)
    {
        verifyInstance(parent);
        layout.invalidateLayout(parent);
    }

    public void addLayoutComponent(String name, Component component)
    {
        try
        {
            component.getParent().invalidate();
        }
        catch (Exception exception) { }
    }

    public void removeLayoutComponent(Component component)
    {
        try
        {
            component.getParent().invalidate();
        }
        catch (Exception exception) { }
    }

    public Dimension preferredLayoutSize(Container parent)
    {
        verifyInstance(parent);
        return layout.preferredLayoutSize(parent);
    }

    public Dimension minimumLayoutSize(Container parent)
    {
        verifyInstance(parent);
        return layout.minimumLayoutSize(parent);
    }

    public void layoutContainer(Container parent)
    {
        verifyInstance(parent);
        layout.layoutContainer(parent);
    }
}
