package cz.sa.ybus.server.infrastructure.dao.szdc;

import java.util.List;

import org.joda.time.DateTime;
import org.joda.time.Duration;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import cz.sa.ybus.domain.bus.VehicleCategory;
import cz.sa.ybus.domain.timetable.Country;
import cz.sa.ybus.server.infrastructure.db.daosupport.AbstractDao;
import cz.sa.ybus.server.infrastructure.db.daosupport.QueryBuilder;
import cz.sa.ybus.server.infrastructure.db.entity.bus.BusConnectionVO;
import cz.sa.ybus.server.infrastructure.db.entity.bus.BusStationVO;
import cz.sa.ybus.server.infrastructure.util.SzdcUtils;

@Repository
@Transactional(readOnly = true)
public class SzdcDao extends AbstractDao {
  
  private static final Duration DEPARTURE_INTERVAL = Duration.standardMinutes(30);

  @SuppressWarnings("unchecked")
  public List<BusStationVO> getCurrentBusStations() {

    QueryBuilder hql = QueryBuilder.hql();

    DateTime now = DateTime.now();
    DateTime departureUpperLimit = now.plus(DEPARTURE_INTERVAL);
    DateTime departureLowerLimit = now.minus(SzdcUtils.MAX_EXPECTED_DELAY);
    
    hql.append("select distinct bs from BusStationVO bs ");
    hql.append("join fetch bs.busConnection bc");
    hql.append("join fetch bs.station s");
    hql.append("join fetch s.city c");
    hql.append("join bc.vehicles bcv");
    hql.append("join bcv.busType bt");
    hql.append("where s.sr70Number is not null");
    hql.append("and ((bs.departure >= :dateTimeNow");
    hql.append("and bs.departure <= :dateTimeIncrement)");
    hql.append("or (bs.arrival >= :dateTimeNow");
    hql.append("and bs.arrival <= :dateTimeIncrement))");
    hql.append("and c.country = :country");
    hql.append("and bt.vehicleCategory = :vehicleCategory");
    hql.setParameter("dateTimeNow", departureLowerLimit);
    hql.setParameter("dateTimeIncrement", departureUpperLimit);
    hql.setParameter("country", Country.CZ);
    hql.setParameter("vehicleCategory", VehicleCategory.RAIL_CAR);

    return hql.createQuery(getSession()).list();
  }
  
  /**
   * Pri snaze optimalizovat tento hql dotaz tim, ze pomoci clausule WITH vybere jen ty potrebene bs.departue, bs.arrival jsme narazily na omezeni hibernatu
   * kdy dochazelo k chybovemu hlaseni "with clause can only reference columns in the driving table". Tento problem zatim neni v hibernatu vyresen 8.8.2017
   * @see <a href="https://hibernate.atlassian.net/browse/HHH-2772">hibernate.atlassian.net/browse/HHH-2772</a>
   */
  @SuppressWarnings("unchecked")
  public List<BusConnectionVO> getTodayBusConnections() {

    QueryBuilder hql = QueryBuilder.hql();
    
    DateTime now = DateTime.now();

    hql.append("select distinct bc from BusConnectionVO bc ");
    hql.append("join bc.busStations bs");
    hql.append("join bs.station s");
    hql.append("join fetch bc.busStations bs2");
    hql.append("join fetch bs2.station s2");
    hql.append("join s.city c");
    hql.append("join fetch s2.city c2");
    hql.append("join bc.vehicles bcv");
    hql.append("join bcv.busType bt");
    hql.append("where s.sr70Number is not null");
    hql.append("and ((bs.departure >= :dateTimeStart");
    hql.append("and bs.departure <= :dateTimeEnd)");
    hql.append("or (bs.arrival >= :dateTimeStart");
    hql.append("and bs.arrival <= :dateTimeEnd))");
    hql.append("and c.country = :country");
    hql.append("and bt.vehicleCategory = :vehicleCategory");
    hql.setParameter("dateTimeStart", now.minus(SzdcUtils.MAX_EXPECTED_DELAY));
    hql.setParameter("dateTimeEnd", now);
    hql.setParameter("country", Country.CZ);
    hql.setParameter("vehicleCategory", VehicleCategory.RAIL_CAR);

    return hql.createQuery(getSession()).list();
  }

}
