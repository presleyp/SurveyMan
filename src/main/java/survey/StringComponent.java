package survey;

public class StringComponent extends Component {

    public final String data;

    public StringComponent(String data, int row, int col) {
        super(row, col);
        this.data = data;
    }
    
    @Override
    public boolean equals(Component c) {
        if (c instanceof StringComponent)
            return this.data.equals(((StringComponent) c).data);
        else return false;
    }

    @Override
    public String toString() {
        return data;
    }
}