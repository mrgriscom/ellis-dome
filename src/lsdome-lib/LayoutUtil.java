import java.util.*;
import processing.core.*;
import processing.data.*;

enum PanelLayout {
    _2,
    _13,
    _24
}

public class LayoutUtil {
    
    /**
     * Axial coordinates are a coordinate system referring to vertices of a triangular grid. The
     * grid is oriented such that triangle edges are horizontal (coincident with the x-axis) and at
     * +/-60 degrees. For a grid coordinate (u, v), u refers to coordinates increasing from the
     * origin in the line 60 degrees counter-clockwise from the +x axis, while v refers to
     * increasing along the x-axis.
     */

    static double SQRT_3 = Math.sqrt(3.);
    static double PI = Math.PI;

    // Convenience methods to make vector math easier. Input arguments are treated as constants.

    // Create a new vector (x, y)
    static PVector V(double x, double y) {
        return new PVector((float)x, (float)y);
    }

    // Clone a vector
    static PVector V(PVector v) {
        return V(v.x, v.y);
    }

    // Return a + b
    static PVector Vadd(PVector a, PVector b) {
        return PVector.add(a, b);
    }

    // Return a - b
    static PVector Vsub(PVector a, PVector b) {
        return Vadd(a, Vmult(b, -1.));
    }

    // Return k * a
    static PVector Vmult(PVector v, double k) {
        return PVector.mult(v, (float)k);
    }

    // Return v rotated counter-clockwise by theta radians
    static PVector Vrot(PVector v, double theta) {
        PVector rot = V(v);
        rot.rotate((float)theta);
        return rot;
    }

    // Compute a basis transformation for vector p, where u is the transformation result of basis vector U (1, 0),
    // and v is the transformation of basis V (0, 1)
    static PVector basisTransform(PVector p, PVector U, PVector V) {
        return Vadd(Vmult(U, p.x), Vmult(V, p.y));
    }

    // Spacing between points of a triangular grid of size 'n' where distance between edge points on opposing
    // panels is 'k' times the distance between adjacent points on the same panel.
    static double pixelSpacing(int n) {
        // Even spacing across the panel gap. .5*SQRT_3 may be a better choice as it makes the density more
        // consistent across the gap (the gaps jump out less), but makes a bunch of other logic more complicated.
        double k = 1.;
        return 1. / (n - 1 + k * SQRT_3);
    }
    
    static interface Transform {
        public PVector transform(PVector p);
    }

    // Convert a set of points in bulk according to some transformation function.
    static ArrayList<PVector> transform(ArrayList<PVector> points, Transform tx) {
        ArrayList<PVector> transformed = new ArrayList<PVector>();
        for (PVector p : points) {
            transformed.add(tx.transform(p));
        }
        return transformed;
    }

    // Transformation that translates a point by 'offset'
    static Transform translate(final PVector offset) {
        return new Transform() {
            public PVector transform(PVector p) {
                return Vadd(p, offset);
            }
        };
    }
 
    // Evenly fill a triangle with a grid of points of size n. The triangle filled is an equilateral triangle
    // with points at (0, 0), (1, 0), and (.5, sqrt(3)/2). Returns a list of points traversed in a boustrophedon
    // manner, starting near the origin, proceeding left/right, then upward. The point near (0, 0) will thus be
    // known as the 'entry' point, and the top-most point as the 'exit' point.
    static ArrayList<TriCoord> fillTriangle(int n) {
        ArrayList<TriCoord> coords = new ArrayList<TriCoord>();
        for (int row = 0; row < n; row++) {
            boolean reversed = (row % 2 == 1);
            int width = n - row;
            for (int col = 0; col < width; col++) {
                int c = (reversed ? width - 1 - col : col);
                TriCoord tc = TriCoord.fromParts(CoordType.PIXEL, Axis.U, row, Axis.V, c, PanelOrientation.A, n);
                coords.add(tc);
            }
        }
        return coords;
    }

    // Fill a triangle using the sizing and entry/exit semantics from above, where the triangle's origin is
    // the axial UV coordinate 'entry' and rotated clockwise by angle 60deg * rot
    static ArrayList<DomeCoord> fillTriangle(final PVector entry, final int rot, int n) {
        // TODO can these be derived from first principles?
        int[][] offsets = {{0, 0, -1}, {-1, 0, -1}, {-1, 0, 0}, {-1, -1, 0}, {0, -1, 0}, {0, -1, -1}};

        int u0 = (int)entry.x;
        int v0 = (int)entry.y;
        int[] o = offsets[MathUtil.mod(rot, 6)];
        TriCoord panel = TriCoord.fromParts(CoordType.PANEL, Axis.U, u0 + o[0], Axis.V, v0 + o[1],
                                            MathUtil.mod(rot, 2) == 0 ? PanelOrientation.A : PanelOrientation.B, 0);

        ArrayList<DomeCoord> coords = new ArrayList<DomeCoord>(); 
        for (TriCoord c : fillTriangle(n)) {
            coords.add(new DomeCoord(panel, c.rotate(rot)));
        }
        return coords;
    }
    
    // Get the exit point for a triangle fill
    static PVector exitPointForFill(PVector entry, int rot) {
        return axialNeighbor(entry, rot - 1);
    }

    static ArrayList<DomeCoord> fillFan(int orientation, int segments, int pixels) {
        return fillFan(orientation, segments, pixels, V(0, 0));
    }

    // Fill a fan of triangles proceeding in a clockwise fashion until a complete hexagon whose perimeter
    // intersects the origin is filled. 'segments' is the number of triangular segments to fill (up to 6).
    // 'pixels' is the fill density within each triangle. 'orientation' is the initial orientation in
    // which the long axis of the hexagon follows the angle specified by 'rot' semantics above.
    static ArrayList<DomeCoord> fillFan(int orientation, int segments, int pixels, PVector entry) {
        ArrayList<DomeCoord> points = new ArrayList<DomeCoord>();
        int rot = orientation;
        for (int i = 0; i < segments; i++) {
            points.addAll(fillTriangle(entry, rot, pixels));
            entry = exitPointForFill(entry, rot);
            rot += 1;
        }
        return points;
    }
    
    // Convert tri-grid u/v/w coordinates to cartesian x/y coordinates. Points are placed such that spacing
    // between two adjacent points will match the spacing between an edge point and the opposing point of a
    // neighboring triangle.
    static PVector coordToXy(DomeCoord c) {
        double spacing = pixelSpacing(c.pixel.panel_length);
        PVector root = c.panel.toV();
        PVector px = c.pixel.toV();
        PVector offset = V(1/SQRT_3, 1/SQRT_3);
        if (c.panel.getOrientation() == PanelOrientation.B) {
            root = Vadd(root, V(1, 1));
            px = Vsub(px, V(c.pixel.panel_length - 1, c.pixel.panel_length - 1));
            offset = Vmult(offset, -1);
        }
        return axialToXy(Vadd(root, Vmult(Vadd(px, offset), spacing)));
    }

    // All metadata associated with a particular layout of panels.
    static abstract class PanelConfig {
        double radius;  // Max radius of panel configuration, in panel lengths
        int[] arms;     // Number of panels per fadecandy 'arm'
        PVector origin; // Center the layout on this point (in UV coordinates)
        double theta;   // Rotate the layout counter-clockwise by this many degrees

        public PanelConfig(int num_panels, double radius, int[] arms, PVector origin, double theta) {
            this.radius = radius;
            this.arms = arms;
            this.origin = origin;
            this.theta = theta;

            int panel_count = 0;
            for (int n : arms) {
                panel_count += n;
            }
            assert num_panels == panel_count;
        }

        // Fill the lsdome configuration with pixels
        abstract ArrayList<DomeCoord> fill(int n);

        // Convert the filled grid points to a mapping of grid points to their actual positions.
        HashMap<DomeCoord, PVector> coordsToXy(ArrayList<DomeCoord> coords) {
            HashMap<DomeCoord, PVector> coordToPoint = new HashMap<DomeCoord, PVector>();
            PVector offset = axialToXy(origin);
            for (DomeCoord c : coords) {
                PVector p = Vrot(Vsub(coordToXy(c), offset), Math.toRadians(theta));
                coordToPoint.put(c, p);
            }
            return coordToPoint;
        }
    }

    // Note: this layout is off-center.
    static PanelConfig _2 = new PanelConfig(2,
                                            2./3.*SQRT_3,
                                            new int[] {2},
                                            V(1/3., 1/3.), 0.) {
            ArrayList<DomeCoord> fill(int n) {
                return fillFan(0, 2, n);
            }
        };
    static PanelConfig _13 = new PanelConfig(13,
                                             Math.sqrt(7/3.),  // just trust me
                                             new int[] {4, 4, 4, 1},
                                             V(1/3., 1/3.), 0.) {
            ArrayList<DomeCoord> fill(int n) {
                final PVector[] entries = {V(1, 0), V(0, 1), V(0, 0)};
                ArrayList<DomeCoord> points = new ArrayList<DomeCoord>();
                for (int i = 0; i < 3; i++) {
                    points.addAll(fillFan(2*i+1, 4, n, entries[i]));
                }
                points.addAll(fillTriangle(V(0, 0), 0, n));
                return points;
            }
        };
    static PanelConfig _24 = new PanelConfig(24,
                                             2.,
                                             new int[] {4, 4, 4, 4, 4, 4},
                                             V(0, 0), 0.) {
            ArrayList<DomeCoord> fill(int n) {
                ArrayList<DomeCoord> points = new ArrayList<DomeCoord>();
                for (int i = 0; i < 6; i++) {
                    points.addAll(fillFan(i, 4, n));
                }
                return points;
            }
        };

    static PanelConfig getPanelConfig(PanelLayout config) {
        switch (config) {
        case _2:
            return _2;
        case _13:
            return _13;
        case _24:
            return _24;
        default:
            throw new RuntimeException();
        }
    }
    
    // Generates the JSON config file for OPC simulator
    static void generateOPCSimLayout(ArrayList<PVector> points, PApplet app, String fileName, boolean skipDeadPixels) {
        JSONArray values = new JSONArray();
        
        int k = 0;
        for (int i = 0; i < points.size(); i++) {
            if (skipDeadPixels && OPC.isDeadPixel(i)) {
                continue;
            }
            
            JSONObject point = new JSONObject();
            
            float[] coordinates = new float[3];
            coordinates[0] = 2 * points.get(i).x;
            coordinates[1] = 2 * points.get(i).y;
            coordinates[2] = 2 * points.get(i).z;
            
            point.setJSONArray("point", new JSONArray(new FloatList(coordinates)));
            
            values.setJSONObject(k, point);
            k += 1;
        }
        
        app.saveJSONArray(values, fileName);
    }

    // Convert a 2-vector of (U, V) coordinates from the axial coordinate scheme into (x, y) cartesian coordinates
    static PVector axialToXy(PVector p) {
        PVector U = V(.5, .5 * SQRT_3);
        PVector V = V(1., 0.);
        return basisTransform(p, U, V);
    }
    
    // Convert (x, y) coordinate p to screen pixel coordinates where top-left is pixel (0, 0) and bottom-right is
    // pixel (width, height). 'span' is the size of the viewport in world coordinates, where size means width if horizSpan is
    // true and height if horizSpan is false. World origin is in the center of the viewport.
    static PVector xyToScreen(PVector p, int width, int height, double span, boolean horizSpan) {
        double scale = span / (horizSpan ? width : height);
        PVector U = V(1. / scale, 0);
        PVector V = V(0, -1. / scale);
        PVector offset = Vmult(V(width, height), .5);
        return Vadd(basisTransform(p, U, V), offset);
    }

    // Inverse of xyToScreen
    static PVector screenToXy(PVector p, int width, int height, double span, boolean horizSpan) {
        double scale = span / (horizSpan ? width : height);
        PVector U = V(scale, 0);
        PVector V = V(0, -scale);
        PVector offset = Vmult(V(width, height), .5);
        return basisTransform(Vsub(p, offset), U, V);
    }

    // Convert (x, y) coordinate to polar coordinates (radius, theta [counter-clockwise])
    static PVector xyToPolar(PVector p) {
        return V(p.mag(), Math.atan2(p.y, p.x));
    }

    // Convert polar coordinates (radius, theta [counter-clockwise]) to cartesian (x, y)
    static PVector polarToXy(PVector p) {
        double r = p.x;
        double theta = p.y;
        return Vrot(V(r, 0), theta);
    }
    
    // Return the adjacent axial coordinate moving from 'p' in direction 'rot'
    static PVector axialNeighbor(PVector p, int rot) {
        int axis = MathUtil.mod(rot, 3);
        boolean hemi = (MathUtil.mod(rot, 6) < 3);
        int du = (axis == 0 ? 0 : (hemi ? -1 : 1));
        int dv = (axis == 2 ? 0 : (hemi ? 1 : -1));
        return V(p.x + du, p.y + dv);
    }
    
    // Return whether two axial coordinates are adjacent lattice points
    static boolean axialCoordsAdjacent(PVector a, PVector b) {
        int du = (int)a.x - (int)b.x;
        int dv = (int)a.y - (int)b.y;
        return (du >= -1 && du <= 1 && dv >= -1 && dv <= 1 && du != dv);
    }
    
    // Number of pixels in a single panel of size n
    static int pixelsPerPanel(int n) {
        return n * (n + 1) / 2;
    }

}
