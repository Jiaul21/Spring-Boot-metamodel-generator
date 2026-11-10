package com.jiaul.metamodelgenerator.testmodel;

import com.jiaul.metamodelgenerator.annotation.ModelFields;
import java.util.List;

@ModelFields
public class TreeNode {
    private Long id;
    private String name;
    private TreeNode parent;
    private List<TreeNode> children;

    public TreeNode() {
    }

    public TreeNode(Long id, String name, TreeNode parent, List<TreeNode> children) {
        this.id = id;
        this.name = name;
        this.parent = parent;
        this.children = children;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public TreeNode getParent() {
        return parent;
    }

    public void setParent(TreeNode parent) {
        this.parent = parent;
    }

    public List<TreeNode> getChildren() {
        return children;
    }

    public void setChildren(List<TreeNode> children) {
        this.children = children;
    }
}
