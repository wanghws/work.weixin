

import java.util.List;


public class DepartmentUserResponse extends Response{
    private List<DepartmentUser> userlist;

    public List<DepartmentUser> getUserlist() {
        return userlist;
    }

    public void setUserlist(List<DepartmentUser> userlist) {
        this.userlist = userlist;
    }
}
