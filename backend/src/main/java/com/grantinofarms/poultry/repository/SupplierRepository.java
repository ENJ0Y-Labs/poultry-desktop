package com.grantinofarms.poultry.repository;

import com.grantinofarms.poultry.dto.SupplierResponse;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public class SupplierRepository {
    private final JdbcTemplate jdbc;
    public SupplierRepository(JdbcTemplate jdbc){this.jdbc=jdbc;}

    public List<SupplierResponse> findAll(String farmId){
        return jdbc.query("""
                SELECT id,farm_id,name,supplier_type,phone,email,address,notes,status
                FROM suppliers WHERE farm_id=? ORDER BY name
                """,(rs,n)->new SupplierResponse(rs.getString("id"),rs.getString("farm_id"),rs.getString("name"),
                rs.getString("supplier_type"),rs.getString("phone"),rs.getString("email"),rs.getString("address"),
                rs.getString("notes"),rs.getString("status")),farmId);
    }

    public SupplierResponse find(String farmId,String id){
        return jdbc.query("""
                SELECT id,farm_id,name,supplier_type,phone,email,address,notes,status
                FROM suppliers WHERE farm_id=? AND id=?
                """,(rs,n)->new SupplierResponse(rs.getString("id"),rs.getString("farm_id"),rs.getString("name"),
                rs.getString("supplier_type"),rs.getString("phone"),rs.getString("email"),rs.getString("address"),
                rs.getString("notes"),rs.getString("status")),farmId,id).stream().findFirst().orElse(null);
    }

    public void insert(String id,String farmId,String name,String phone,String email,String address,String notes,String now){
        jdbc.update("""
                INSERT INTO suppliers(id,farm_id,name,supplier_type,phone,email,address,notes,status,created_at,updated_at)
                VALUES(?,?,?,?,?,?,?,?,?,?,?)
                """,id,farmId,name,"SUPPLIER",phone,email,address,notes,"ACTIVE",now,now);
    }

    public void update(String id,String farmId,String name,String phone,String email,String address,String notes,String status,String now){
        jdbc.update("""
                UPDATE suppliers SET name=?,phone=?,email=?,address=?,notes=?,status=?,updated_at=?
                WHERE id=? AND farm_id=?
                """,name,phone,email,address,notes,status,now,id,farmId);
    }
}
